package org.sirohi.smartnotebook.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sirohi.smartnotebook.service.CredentialProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gateway for OpenAI API (ChatGPT).
 * Credentials are fetched dynamically from the database.
 */
@Component
public class OpenAiGateway implements ModelGateway {

    private static final String PROVIDER_ID = "openai";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-3.5-turbo";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";

    private final CredentialProvider credentialProvider;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private static final Logger log = LoggerFactory.getLogger(OpenAiGateway.class);

    public OpenAiGateway(CredentialProvider credentialProvider, ObjectMapper objectMapper) {
        this.credentialProvider = credentialProvider;
        this.objectMapper = objectMapper;
        this.webClientBuilder = WebClient.builder();
        log.info("OpenAiGateway initialized (credentials from database)");
    }

    private WebClient buildClient() {
        String apiKey = credentialProvider.getApiKey(PROVIDER_ID);
        String baseUrl = credentialProvider.getBaseUrl(PROVIDER_ID);

        return webClientBuilder
                .baseUrl(baseUrl != null ? baseUrl : DEFAULT_BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    private boolean isEnabled() {
        return credentialProvider.isProviderAvailable(PROVIDER_ID);
    }

    @Override
    @Retry(name = "llmApi")
    @CircuitBreaker(name = "llmApi")
    public CompletionResponse complete(CompletionRequest request) {
        if (!isEnabled())
            throw new ModelGatewayException("OpenAI provider is not enabled or API key is missing");

        long start = System.currentTimeMillis();
        Map<String, Object> body = createChatBody(request, false);

        try {
            JsonNode response = buildClient().post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            long latency = System.currentTimeMillis() - start;

            if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
                throw new ModelGatewayException("Empty response from OpenAI");
            }

            JsonNode choice = response.get("choices").get(0);
            String text = choice.get("message").get("content").asText();
            int promptTokens = response.path("usage").path("prompt_tokens").asInt();
            int evalTokens = response.path("usage").path("completion_tokens").asInt();

            log.debug("OpenAI completion in {}ms (prompt={}, completion={})", latency, promptTokens, evalTokens);

            return new CompletionResponse(text, promptTokens, evalTokens, latency, request.model());
        } catch (Exception e) {
            throw new ModelGatewayException("OpenAI completion failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Retry(name = "llmApi")
    @CircuitBreaker(name = "llmApi")
    public Flux<String> completeStreaming(CompletionRequest request) {
        if (!isEnabled())
            return Flux.error(new ModelGatewayException("OpenAI provider is disabled"));

        Map<String, Object> body = createChatBody(request, true);

        return buildClient().post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(line -> {
                    if (line.equals("[DONE]"))
                        return Flux.empty();
                    try {
                        JsonNode node = objectMapper.readTree(line);
                        if (node.has("choices") && !node.get("choices").isEmpty()) {
                            JsonNode choice = node.get("choices").get(0);
                            if (choice.has("delta") && choice.get("delta").has("content")) {
                                return Flux.just(choice.get("delta").get("content").asText());
                            }
                        }
                        return Flux.empty();
                    } catch (Exception e) {
                        return Flux.empty();
                    }
                })
                .onErrorResume(e -> Flux.error(new ModelGatewayException("OpenAI streaming failed", e)));
    }

    @Override
    @Retry(name = "llmApi")
    @CircuitBreaker(name = "llmApi")
    public EmbeddingResponse embed(EmbeddingRequest request) {
        if (!isEnabled())
            throw new ModelGatewayException("OpenAI provider is disabled");

        long start = System.currentTimeMillis();
        Map<String, Object> body = Map.of(
                "model", request.model() != null ? request.model() : DEFAULT_EMBEDDING_MODEL,
                "input", request.text());

        try {
            JsonNode response = buildClient().post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            long latency = System.currentTimeMillis() - start;

            JsonNode data = response.get("data").get(0);
            JsonNode vecNode = data.get("embedding");
            float[] vector = new float[vecNode.size()];
            for (int i = 0; i < vecNode.size(); i++) {
                vector[i] = (float) vecNode.get(i).asDouble();
            }

            log.debug("OpenAI embedding in {}ms (dims={})", latency, vector.length);

            return new EmbeddingResponse(vector, vector.length, latency, request.model());
        } catch (Exception e) {
            throw new ModelGatewayException("OpenAI embedding failed", e);
        }
    }

    @Override
    public ModelHealth health() {
        if (!isEnabled())
            return new ModelHealth(false, PROVIDER_ID, "N/A", "Disabled/No Key");
        try {
            buildClient().get().uri("/models").retrieve().toBodilessEntity().block();
            return new ModelHealth(true, PROVIDER_ID, DEFAULT_MODEL, "Connected");
        } catch (Exception e) {
            return new ModelHealth(false, PROVIDER_ID, DEFAULT_MODEL, e.getMessage());
        }
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    private Map<String, Object> createChatBody(CompletionRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.model() != null ? request.model() : DEFAULT_MODEL);
        body.put("stream", stream);

        List<Map<String, String>> messages = new java.util.ArrayList<>();
        if (request.systemPrompt() != null) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));

        body.put("messages", messages);
        return body;
    }
}
