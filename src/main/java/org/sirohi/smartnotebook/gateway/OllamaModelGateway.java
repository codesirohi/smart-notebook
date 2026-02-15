package org.sirohi.smartnotebook.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sirohi.smartnotebook.config.ModelConfig;
import org.sirohi.smartnotebook.model.ModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama-based ModelGateway for local development.
 * Uses RestTemplate for blocking calls and WebClient for streaming.
 */
@Component
public class OllamaModelGateway implements ModelGateway {

    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final String baseUrl;
    private final String defaultModel;
    private final ObjectMapper objectMapper;
    private final ModelConfig.ProviderConfig providerConfig;

    private static final Logger log = LoggerFactory.getLogger(OllamaModelGateway.class);

    public OllamaModelGateway(ModelConfig modelConfig, ObjectMapper objectMapper) {
        this.providerConfig = modelConfig.getProviders().get(ModelProvider.OLLAMA);
        this.objectMapper = objectMapper;

        this.baseUrl = (providerConfig != null && providerConfig.getBaseUrl() != null)
                ? providerConfig.getBaseUrl()
                : "http://localhost:11434";

        // Fallback to configured default extraction model if specific ollama model
        // aliases aren't set
        this.defaultModel = modelConfig.getDefaultExtractionModel();

        // Blocking HTTP client for non-streaming calls
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restTemplate = new RestTemplate(factory);

        // Reactive HTTP client for streaming
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    private boolean isEnabled() {
        return providerConfig == null || providerConfig.isEnabled();
    }

    @Override
    public CompletionResponse complete(CompletionRequest request) {
        if (!isEnabled())
            throw new ModelGatewayException("Ollama provider is disabled");

        long start = System.currentTimeMillis();

        String model = request.model() != null ? request.model() : defaultModel;

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", request.userPrompt());
        body.put("stream", false);
        if (request.systemPrompt() != null) {
            body.put("system", request.systemPrompt());
        }

        try {
            @SuppressWarnings("unchecked")
            var response = restTemplate.postForObject(
                    baseUrl + "/api/generate", body, Map.class);

            long latency = System.currentTimeMillis() - start;
            String text = (String) response.get("response");

            return new CompletionResponse(
                    text,
                    0, 0, // Ollama doesn't return token counts in basic mode
                    latency,
                    model);
        } catch (RestClientException e) {
            throw new ModelGatewayException("Ollama completion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Stream completion token-by-token from Ollama's /api/generate (stream: true).
     * Each NDJSON line contains {"response": "token_text", "done": false/true}.
     * We extract the "response" field and emit each token as a Flux element.
     */
    @Override
    public Flux<String> completeStreaming(CompletionRequest request) {
        if (!isEnabled())
            return Flux.error(new ModelGatewayException("Ollama provider is disabled"));

        String model = request.model() != null ? request.model() : defaultModel;

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", request.userPrompt());
        body.put("stream", true);
        if (request.systemPrompt() != null) {
            body.put("system", request.systemPrompt());
        }

        return webClient.post()
                .uri("/api/generate")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(line -> {
                    try {
                        JsonNode node = objectMapper.readTree(line);
                        String token = node.has("response") ? node.get("response").asText() : "";
                        boolean done = node.has("done") && node.get("done").asBoolean();
                        if (done) {
                            // Emit last token (if any) and complete
                            return token.isEmpty() ? Flux.empty() : Flux.just(token);
                        }
                        return Flux.just(token);
                    } catch (Exception e) {
                        log.warn("Failed to parse streaming response line: {}", line, e);
                        return Flux.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Streaming completion failed: {}", e.getMessage());
                    return Flux.error(new ModelGatewayException(
                            "Ollama streaming failed: " + e.getMessage(), e));
                });
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        if (!isEnabled())
            throw new ModelGatewayException("Ollama provider is disabled");

        long start = System.currentTimeMillis();

        String model = request.model() != null ? request.model() : defaultModel;

        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", request.text());

        try {
            @SuppressWarnings("unchecked")
            var response = restTemplate.postForObject(
                    baseUrl + "/api/embeddings", body, Map.class);

            @SuppressWarnings("unchecked")
            List<Number> embeddingList = (List<Number>) response.get("embedding");
            float[] vector = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                vector[i] = embeddingList.get(i).floatValue();
            }

            long latency = System.currentTimeMillis() - start;

            return new EmbeddingResponse(vector, vector.length, latency, model);
        } catch (RestClientException e) {
            throw new ModelGatewayException("Ollama embedding failed: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModelHealth health() {
        if (!isEnabled())
            return new ModelHealth(false, "ollama", defaultModel, "Disabled");
        try {
            restTemplate.getForObject(baseUrl + "/api/tags", Map.class);
            return new ModelHealth(true, "ollama", defaultModel, "Connected");
        } catch (Exception e) {
            return new ModelHealth(false, "ollama", defaultModel, e.getMessage());
        }
    }

    @Override
    public String providerId() {
        return "ollama";
    }
}
