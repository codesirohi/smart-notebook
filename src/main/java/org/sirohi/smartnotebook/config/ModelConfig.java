package org.sirohi.smartnotebook.config;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for LLM providers.
 * Structure:
 * app.models:
 * openai: ...
 * anthropic: ...
 * google: ...
 * ollama: ...
 */
@Configuration
@Validated
@ConfigurationProperties(prefix = "app.models")
public class ModelConfig {

    private final Map<String, ProviderConfig> providers = new HashMap<>();

    private String defaultExtractionModel = "tinyllama";
    private String defaultEmbeddingModel = "nomic-embed-text";

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public ProviderConfig getProvider(String name) {
        return providers.get(name);
    }

    public String getDefaultExtractionModel() {
        return defaultExtractionModel;
    }

    public void setDefaultExtractionModel(String defaultExtractionModel) {
        this.defaultExtractionModel = defaultExtractionModel;
    }

    public String getDefaultEmbeddingModel() {
        return defaultEmbeddingModel;
    }

    public void setDefaultEmbeddingModel(String defaultEmbeddingModel) {
        this.defaultEmbeddingModel = defaultEmbeddingModel;
    }

    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl;
        private boolean enabled = true;
        private Map<String, String> models = new HashMap<>();

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, String> getModels() {
            return models;
        }

        public void setModels(Map<String, String> models) {
            this.models = models;
        }
    }
}
