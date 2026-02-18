package org.sirohi.smartnotebook.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sirohi.smartnotebook.config.ModelConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gateway for Anthropic API (Claude).
 */
@Component
public class AnthropicGateway implements ModelGateway {

    private final WebClient webClient;
    private final ModelConfig.ProviderConfig config;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(AnthropicGateway.class);

    public AnthropicGateway(ModelConfig modelConfig, ObjectMapper objectMapper) {
        this.config = modelConfig.getProvider("anthropic");
        this.objectMapper = objectMapper;

        String baseUrl = (config != null && config.getBaseUrl() != null)
                ? config.getBaseUrl()
                : "https://api.anthropic.com/v1";

        String apiKey = (config != null) ? config.getApiKey() : null;

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    private boolean isEnabled() {
        return config != null && config.isEnabled() && config.getApiKey() != null;
    }

    @Override
    public CompletionResponse complete(CompletionRequest request) {
        if (!isEnabled())
            throw new ModelGatewayException("Anthropic provider is not enabled");

        long start = System.currentTimeMillis();
        Map<String, Object> body = createMessagesBody(request, false);

        try {
            JsonNode response = webClient.post()
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

            return new CompletionResponse(text, promptTokens, evalTokens, latency, request.model());
        } catch (Exception e) {
            throw new ModelGatewayException("Anthropic completion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> completeStreaming(CompletionRequest request) {
        if (!isEnabled())
            return Flux.error(new ModelGatewayException("Anthropic provider is disabled"));

        Map<String, Object> body = createMessagesBody(request, true);

        return webClient.post()
                .uri("/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(line -> {
                    // Anthropic SSE events: event: content_block_delta, data: { delta: { text:
                    // "..." } }
                    // Does WebClient split by "event:"? No, bodyToFlux(String) usually splits by
                    // newlines or raw chunks.
                    // For proper SSE, we should use bodyToFlux(ServerSentEvent.class) or better
                    // parsing.
                    // But simplified here assuming standard SSE format where line starts with
                    // "data: "

                    // Actually, let's use a simpler heuristic for raw stream:
                    // Anthropic sends "event: ..." lines and "data: ..." lines.
                    // We look for "data: " lines.
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
        // Anthropic does not support embeddings yet (officially recommends Voy or
        // others).
        throw new ModelGatewayException("Anthropic does not support embeddings API via this gateway.");
    }

    @Override
    public ModelHealth health() {
        if (!isEnabled())
            return new ModelHealth(false, "anthropic", "N/A", "Disabled/No Key");

        try {
            // Ping by listing 1 model
            webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return new ModelHealth(true, "anthropic", "default", "Connected");
        } catch (Exception e) {
            return new ModelHealth(false, "anthropic", "default", "Error: " + e.getMessage());
        }
    }

    @Override
    public String providerId() {
        return "anthropic";
    }

    private Map<String, Object> createMessagesBody(CompletionRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.model() != null ? request.model() : "claude-3-haiku-20240307");
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
