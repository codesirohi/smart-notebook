package org.sirohi.smartnotebook.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama-based ModelGateway for local development (Phase 1).
 * Uses raw REST calls via RestTemplate — no Spring AI dependency.
 */
@Component
@Profile("local")
public class OllamaModelGateway implements ModelGateway {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String defaultModel;

    private static final Logger log = LoggerFactory.getLogger(OllamaModelGateway.class);

    public OllamaModelGateway(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:phi3:mini}") String defaultModel) {
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;

        // Configure timeouts
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public CompletionResponse complete(CompletionRequest request) {
        long start = System.currentTimeMillis();

        Map<String, Object> body = new HashMap<>();
        body.put("model", defaultModel);
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
                    defaultModel);
        } catch (RestClientException e) {
            throw new ModelGatewayException("Ollama completion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
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
