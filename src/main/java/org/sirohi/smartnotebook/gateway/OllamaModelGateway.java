package org.sirohi.smartnotebook.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.sirohi.smartnotebook.config.ModelConfig;
import org.sirohi.smartnotebook.service.CredentialProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Ollama-based ModelGateway for local development.
 * Uses RestTemplate for blocking calls and WebClient for streaming.
 * Base URL is fetched from the database if configured.
 *
 * Performance: Connection pooling saves 10-50ms per request by reusing TCP connections.
 */
@Component
public class OllamaModelGateway implements ModelGateway {

    private static final String PROVIDER_ID = "ollama";
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private final CredentialProvider credentialProvider;
    private final String defaultModel;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final HttpClient httpClient;

    private static final Logger log = LoggerFactory.getLogger(OllamaModelGateway.class);

    public OllamaModelGateway(ModelConfig modelConfig, CredentialProvider credentialProvider, ObjectMapper objectMapper) {
        this.credentialProvider = credentialProvider;
        this.objectMapper = objectMapper;
        this.defaultModel = modelConfig.getDefaultExtractionModel();

        // Blocking HTTP client
        this.restTemplate = new RestTemplate();

        // Connection pool for WebClient (saves 10-50ms per request)
        ConnectionProvider connectionProvider = ConnectionProvider.builder("ollama-pool")
                .maxConnections(10)
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .evictInBackground(Duration.ofSeconds(60))
                .build();

        this.httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));

        log.info("OllamaModelGateway initialized with connection pooling (max=10)");
    }

    private String getBaseUrl() {
        String dbBaseUrl = credentialProvider.getBaseUrl(PROVIDER_ID);
        return dbBaseUrl != null ? dbBaseUrl : DEFAULT_BASE_URL;
    }

    private WebClient buildWebClient() {
        return WebClient.builder()
                .baseUrl(getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private boolean isEnabled() {
        return credentialProvider.isProviderEnabled(PROVIDER_ID);
    }

    @Override
    public CompletionResponse complete(CompletionRequest request) {
        if (!isEnabled())
            throw new ModelGatewayException("Ollama provider is disabled");

        long start = System.currentTimeMillis();

        String model = request.model() != null ? request.model() : defaultModel;
        log.debug("Ollama completion request for model: {}", model);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", request.userPrompt());
        body.put("stream", false);
        if (request.systemPrompt() != null) {
            body.put("system", request.systemPrompt());
        }
        body.put("options", buildOllamaOptions(request.parameters()));

        try {
            @SuppressWarnings("unchecked")
            var response = restTemplate.postForObject(
                    getBaseUrl() + "/api/generate", body, Map.class);

            long latency = System.currentTimeMillis() - start;
            String text = (String) response.get("response");
            log.debug("Ollama completion finished in {}ms", latency);

            return new CompletionResponse(
                    text,
                    0, 0, // Ollama doesn't return token counts in basic mode
                    latency,
                    model);
        } catch (RestClientException e) {
            log.error("Ollama completion failed", e);
            throw new ModelGatewayException("Ollama completion failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> completeStreaming(CompletionRequest request) {
        if (!isEnabled())
            return Flux.error(new ModelGatewayException("Ollama provider is disabled"));

        String model = request.model() != null ? request.model() : defaultModel;
        log.debug("Ollama streaming completion request for model: {}", model);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", request.userPrompt());
        body.put("stream", true);
        if (request.systemPrompt() != null) {
            body.put("system", request.systemPrompt());
        }
        body.put("options", buildOllamaOptions(request.parameters()));

        return buildWebClient().post()
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
                            log.debug("Ollama streaming finished");
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
        log.debug("Ollama embedding request for model: {}, text length: {}", model, request.text().length());

        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", request.text());

        try {
            @SuppressWarnings("unchecked")
            var response = restTemplate.postForObject(
                    getBaseUrl() + "/api/embeddings", body, Map.class);

            @SuppressWarnings("unchecked")
            List<Number> embeddingList = (List<Number>) response.get("embedding");
            float[] vector = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                vector[i] = embeddingList.get(i).floatValue();
            }

            long latency = System.currentTimeMillis() - start;
            log.debug("Ollama embedding finished in {}ms", latency);

            return new EmbeddingResponse(vector, vector.length, latency, model);
        } catch (RestClientException e) {
            log.error("Ollama embedding failed", e);
            throw new ModelGatewayException("Ollama embedding failed: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModelHealth health() {
        if (!isEnabled())
            return new ModelHealth(false, PROVIDER_ID, defaultModel, "Disabled");
        try {
            restTemplate.getForObject(getBaseUrl() + "/api/tags", Map.class);
            return new ModelHealth(true, PROVIDER_ID, defaultModel, "Connected");
        } catch (Exception e) {
            return new ModelHealth(false, PROVIDER_ID, defaultModel, e.getMessage());
        }
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    private Map<String, Object> buildOllamaOptions(Map<String, Object> parameters) {
        Map<String, Object> options = new HashMap<>();

        // Prevent chat role hallucinations in plain completion endpoint
        options.put("stop", List.of("User:", "Assistant:"));

        if (parameters == null || parameters.isEmpty()) {
            return options;
        }

        copyOption(parameters, options, "num_predict");
        copyOption(parameters, options, "temperature");
        copyOption(parameters, options, "top_p");
        copyOption(parameters, options, "top_k");

        Object rawNested = parameters.get("options");
        if (rawNested instanceof Map<?, ?> nested) {
            nested.forEach((k, v) -> {
                if (k != null && v != null) {
                    options.put(String.valueOf(k), v);
                }
            });
        }

        return options;
    }

    private void copyOption(Map<String, Object> from, Map<String, Object> to, String key) {
        Object value = from.get(key);
        if (value != null) {
            to.put(key, value);
        }
    }
}
