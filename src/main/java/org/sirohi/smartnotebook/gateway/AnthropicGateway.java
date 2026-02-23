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
 * Gateway for Anthropic API (Claude).
 * Credentials are fetched dynamically from the database.
 */
@Component
public class AnthropicGateway implements ModelGateway {

    private static final String PROVIDER_ID = "anthropic";
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final String DEFAULT_MODEL = "claude-3-haiku-20240307";

    private final CredentialProvider credentialProvider;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private static final Logger log = LoggerFactory.getLogger(AnthropicGateway.class);

    public AnthropicGateway(CredentialProvider credentialProvider, ObjectMapper objectMapper) {
        this.credentialProvider = credentialProvider;
        this.objectMapper = objectMapper;
        this.webClientBuilder = WebClient.builder();
        log.info("AnthropicGateway initialized (credentials from database)");
    }

    private WebClient buildClient() {
        String apiKey = credentialProvider.getApiKey(PROVIDER_ID);
        String baseUrl = credentialProvider.getBaseUrl(PROVIDER_ID);

        return webClientBuilder
                .baseUrl(baseUrl != null ? baseUrl : DEFAULT_BASE_URL)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
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
            throw new ModelGatewayException("Anthropic provider is not enabled or API key is missing");

        long start = System.currentTimeMillis();
        Map<String, Object> body = createMessagesBody(request, false);

        try {
            JsonNode response = buildClient().post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            long latency = System.currentTimeMillis() - start;

            // Anthropic response: content[0].text
            JsonNode content = response.get("content").get(0);
            String text = content.get("text").asText();

            // Usage
            JsonNode usage = response.path("usage");
            int promptTokens = usage.path("input_tokens").asInt();
            int evalTokens = usage.path("output_tokens").asInt();

            log.debug("Anthropic completion in {}ms (prompt={}, completion={})", latency, promptTokens, evalTokens);

            return new CompletionResponse(text, promptTokens, evalTokens, latency, request.model());
        } catch (Exception e) {
            throw new ModelGatewayException("Anthropic completion failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Retry(name = "llmApi")
    @CircuitBreaker(name = "llmApi")
    public Flux<String> completeStreaming(CompletionRequest request) {
        if (!isEnabled())
            return Flux.error(new ModelGatewayException("Anthropic provider is disabled"));

        Map<String, Object> body = createMessagesBody(request, true);

        return buildClient().post()
                .uri("/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(line -> {
                    if (!line.startsWith("data: "))
                        return Flux.empty();
                    String json = line.substring(6);
                    if (json.equals("[DONE]"))
                        return Flux.empty();

                    try {
                        JsonNode node = objectMapper.readTree(json);
                        String type = node.path("type").asText();
                        if ("content_block_delta".equals(type)) {
                            return Flux.just(node.path("delta").path("text").asText());
                        }
                        return Flux.empty();
                    } catch (Exception e) {
                        return Flux.empty();
                    }
                })
                .onErrorResume(e -> Flux.error(new ModelGatewayException("Anthropic streaming failed", e)));
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        // Anthropic does not support embeddings yet
        throw new ModelGatewayException("Anthropic does not support embeddings API via this gateway.");
    }

    @Override
    public ModelHealth health() {
        if (!isEnabled())
            return new ModelHealth(false, PROVIDER_ID, "N/A", "Disabled/No Key");

        try {
            buildClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return new ModelHealth(true, PROVIDER_ID, DEFAULT_MODEL, "Connected");
        } catch (Exception e) {
            return new ModelHealth(false, PROVIDER_ID, DEFAULT_MODEL, "Error: " + e.getMessage());
        }
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    private Map<String, Object> createMessagesBody(CompletionRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.model() != null ? request.model() : DEFAULT_MODEL);
        body.put("stream", stream);
        body.put("max_tokens", 4096);

        if (request.systemPrompt() != null) {
            body.put("system", request.systemPrompt());
        }

        List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "user", "content", request.userPrompt()));

        body.put("messages", messages);
        return body;
    }
}
