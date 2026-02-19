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
 * Gateway for Google Gemini API.
 * Credentials are fetched dynamically from the database.
 */
@Component
public class GeminiGateway implements ModelGateway {

    private static final String PROVIDER_ID = "google";
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String DEFAULT_MODEL = "gemini-1.5-flash";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-004";

    private final CredentialProvider credentialProvider;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private static final Logger log = LoggerFactory.getLogger(GeminiGateway.class);

    public GeminiGateway(CredentialProvider credentialProvider, ObjectMapper objectMapper) {
        this.credentialProvider = credentialProvider;
        this.objectMapper = objectMapper;
        this.webClientBuilder = WebClient.builder();
        log.info("GeminiGateway initialized (credentials from database)");
    }

    private WebClient buildClient() {
        String apiKey = credentialProvider.getApiKey(PROVIDER_ID);
        String baseUrl = credentialProvider.getBaseUrl(PROVIDER_ID);

        return webClientBuilder
                .baseUrl(baseUrl != null ? baseUrl : DEFAULT_BASE_URL)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    private boolean isEnabled() {
        return credentialProvider.isProviderAvailable(PROVIDER_ID);
    }

    @Override
    public CompletionResponse complete(CompletionRequest request) {
        if (!isEnabled())
            throw new ModelGatewayException("Gemini provider is not enabled");

        long start = System.currentTimeMillis();
        Map<String, Object> body = createGeminiBody(request);
        String model = request.model() != null ? request.model() : DEFAULT_MODEL;

        try {
            JsonNode response = buildClient().post()
                    .uri("/models/" + model + ":generateContent")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            long latency = System.currentTimeMillis() - start;

            // Response: candidates[0].content.parts[0].text
            JsonNode candidate = response.get("candidates").get(0);
            String text = candidate.get("content").get("parts").get(0).get("text").asText();

            // Usage Metadata
            int promptTokens = 0;
            int evalTokens = 0;
            if (response.has("usageMetadata")) {
                promptTokens = response.get("usageMetadata").path("promptTokenCount").asInt();
                evalTokens = response.get("usageMetadata").path("candidatesTokenCount").asInt();
            }

            log.debug("Gemini completion in {}ms (prompt={}, completion={})", latency, promptTokens, evalTokens);

            return new CompletionResponse(text, promptTokens, evalTokens, latency, model);
        } catch (Exception e) {
            throw new ModelGatewayException("Gemini completion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> completeStreaming(CompletionRequest request) {
        if (!isEnabled())
            return Flux.error(new ModelGatewayException("Gemini provider is disabled"));

        // Gemini streaming requires complex JSON array parsing
        // Fall back to non-streaming for now
        return Flux.error(
                new ModelGatewayException("Gemini streaming not yet optimized in this gateway version."));
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        if (!isEnabled())
            throw new ModelGatewayException("Gemini provider is disabled");

        long start = System.currentTimeMillis();
        String model = request.model() != null ? request.model() : DEFAULT_EMBEDDING_MODEL;

        Map<String, Object> body = Map.of(
                "content", Map.of("parts", List.of(Map.of("text", request.text()))));

        try {
            JsonNode response = buildClient().post()
                    .uri("/models/" + model + ":embedContent")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            long latency = System.currentTimeMillis() - start;

            JsonNode values = response.get("embedding").get("values");
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = (float) values.get(i).asDouble();
            }

            log.debug("Gemini embedding in {}ms (dims={})", latency, vector.length);

            return new EmbeddingResponse(vector, vector.length, latency, model);
        } catch (Exception e) {
            throw new ModelGatewayException("Gemini embedding failed", e);
        }
    }

    @Override
    public ModelHealth health() {
        if (!isEnabled())
            return new ModelHealth(false, PROVIDER_ID, "N/A", "Disabled/No Key");

        try {
            buildClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models")
                            .queryParam("pageSize", 1)
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

    private Map<String, Object> createGeminiBody(CompletionRequest request) {
        Map<String, Object> body = new HashMap<>();

        // Gemini: contents: [{ role: "user", parts: [{ text: "..." }] }]
        if (request.systemPrompt() != null) {
            body.put("system_instruction", Map.of("parts", Map.of("text", request.systemPrompt())));
        }

        List<Map<String, Object>> contents = new java.util.ArrayList<>();
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", request.userPrompt()))));

        body.put("contents", contents);
        return body;
    }
}
