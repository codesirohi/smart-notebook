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
 * Gateway for OpenAI API (ChatGPT).
 */
@Component
public class OpenAiGateway implements ModelGateway {

    private final WebClient webClient;
    private final ModelConfig.ProviderConfig config;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(OpenAiGateway.class);

    public OpenAiGateway(ModelConfig modelConfig, ObjectMapper objectMapper) {
        this.config = modelConfig.getProvider("openai");
        this.objectMapper = objectMapper;

        String baseUrl = (config != null && config.getBaseUrl() != null)
                ? config.getBaseUrl()
                : "https://api.openai.com/v1";

        String apiKey = (config != null) ? config.getApiKey() : null;

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    private boolean isEnabled() {
        return config != null && config.isEnabled() && config.getApiKey() != null;
    }

    @Override
    public CompletionResponse complete(CompletionRequest request) {
        if (!isEnabled())
            throw new ModelGatewayException("OpenAI provider is not enabled or API key is missing");

        long start = System.currentTimeMillis();
        Map<String, Object> body = createChatBody(request, false);

        try {
            JsonNode response = webClient.post()
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

            return new CompletionResponse(text, promptTokens, evalTokens, latency, request.model());
        } catch (Exception e) {
            throw new ModelGatewayException("OpenAI completion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> completeStreaming(CompletionRequest request) {
        if (!isEnabled())
            return Flux.error(new ModelGatewayException("OpenAI provider is disabled"));

        Map<String, Object> body = createChatBody(request, true);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(line -> {
                    if (line.equals("[DONE]"))
                        return Flux.empty();
                    try {
                        JsonNode node = objectMapper.readTree(line);
                        // OpenAI stream chunks: choices[0].delta.content
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
    public EmbeddingResponse embed(EmbeddingRequest request) {
        if (!isEnabled())
            throw new ModelGatewayException("OpenAI provider is disabled");

        long start = System.currentTimeMillis();
        Map<String, Object> body = Map.of(
                "model", request.model() != null ? request.model() : "text-embedding-3-small",
                "input", request.text());

        try {
            JsonNode response = webClient.post()
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

            return new EmbeddingResponse(vector, vector.length, latency, request.model());
        } catch (Exception e) {
            throw new ModelGatewayException("OpenAI embedding failed", e);
        }
    }

    @Override
    public ModelHealth health() {
        if (!isEnabled())
            return new ModelHealth(false, "openai", "N/A", "Disabled/No Key");
        try {
            // OpenAI doesn't have a lightweight ping, so we check models
            webClient.get().uri("/models").retrieve().toBodilessEntity().block();
            return new ModelHealth(true, "openai", "default", "Connected");
        } catch (Exception e) {
            return new ModelHealth(false, "openai", "default", e.getMessage());
        }
    }

    @Override
    public String providerId() {
        return "openai";
    }

    private Map<String, Object> createChatBody(CompletionRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.model() != null ? request.model() : "gpt-3.5-turbo");
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
