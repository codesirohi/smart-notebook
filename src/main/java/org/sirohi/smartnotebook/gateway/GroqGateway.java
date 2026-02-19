package org.sirohi.smartnotebook.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sirohi.smartnotebook.service.CredentialProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gateway for Groq API - ultra-fast inference for demos.
 * Credentials are fetched dynamically from the database.
 *
 * Groq provides OpenAI-compatible API with significantly lower latency:
 * - Standard LLM: 1-2s response time
 * - Groq: 50-200ms response time (10x faster)
 */
@Component
public class GroqGateway implements ModelGateway {

    private static final String PROVIDER_ID = "groq";
    private static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";

    private final CredentialProvider credentialProvider;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private static final Logger log = LoggerFactory.getLogger(GroqGateway.class);

    public GroqGateway(CredentialProvider credentialProvider, ObjectMapper objectMapper) {
        this.credentialProvider = credentialProvider;
        this.objectMapper = objectMapper;
        this.webClientBuilder = WebClient.builder();
        log.info("GroqGateway initialized (credentials from database)");
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
    public CompletionResponse complete(CompletionRequest request) {
        if (!isEnabled())
            throw new ModelGatewayException("Groq provider is not enabled or API key is missing");

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
                throw new ModelGatewayException("Empty response from Groq");
            }

            JsonNode choice = response.get("choices").get(0);
            String text = choice.get("message").get("content").asText();
            int promptTokens = response.path("usage").path("prompt_tokens").asInt();
            int evalTokens = response.path("usage").path("completion_tokens").asInt();

            log.debug("Groq completion in {}ms (prompt={}, completion={})", latency, promptTokens, evalTokens);

            return new CompletionResponse(text, promptTokens, evalTokens, latency, request.model());
        } catch (Exception e) {
            throw new ModelGatewayException("Groq completion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> completeStreaming(CompletionRequest request) {
        if (!isEnabled())
            return Flux.error(new ModelGatewayException("Groq provider is disabled"));

        Map<String, Object> body = createChatBody(request, true);

        return buildClient().post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(line -> {
                    // Handle SSE format: data: {...}
                    if (line.startsWith("data: ")) {
                        line = line.substring(6);
                    }
                    if (line.equals("[DONE]") || line.trim().isEmpty())
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
                .onErrorResume(e -> Flux.error(new ModelGatewayException("Groq streaming failed", e)));
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        // Groq doesn't provide embedding API
        throw new ModelGatewayException(
                "Groq does not support embeddings. Use Ollama (nomic-embed-text) or OpenAI for embeddings.");
    }

    @Override
    public ModelHealth health() {
        if (!isEnabled())
            return new ModelHealth(false, PROVIDER_ID, DEFAULT_MODEL, "Disabled/No Key");
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

        // Apply parameters if provided
        if (request.parameters() != null) {
            if (request.parameters().containsKey("temperature")) {
                body.put("temperature", request.parameters().get("temperature"));
            }
            if (request.parameters().containsKey("max_tokens")) {
                body.put("max_tokens", request.parameters().get("max_tokens"));
            } else if (request.parameters().containsKey("num_predict")) {
                // Map Ollama's num_predict to max_tokens
                body.put("max_tokens", request.parameters().get("num_predict"));
            }
        }

        List<Map<String, String>> messages = new java.util.ArrayList<>();
        if (request.systemPrompt() != null) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));

        body.put("messages", messages);
        return body;
    }
}
