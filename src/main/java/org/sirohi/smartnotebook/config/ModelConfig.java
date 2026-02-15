package org.sirohi.smartnotebook.config;

import org.sirohi.smartnotebook.model.ModelProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration propertis for LLM providers.
 * Structure:
 * app.models:
 * openai:
 * api-key: ...
 * base-url: ...
 * enabled: true
 * anthropic: ...
 * ...
 */
@Validated
@ConfigurationProperties(prefix = "app.models")
public class ModelConfig {

    private Map<ModelProvider, ProviderConfig> providers = new HashMap<>();
    private String defaultExtractionModel = "tinyllama"; // Default for ingestion
    private String defaultEmbeddingModel = "all-minilm"; // Default for embeddings

    public Map<ModelProvider, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<ModelProvider, ProviderConfig> providers) {
        this.providers = providers;
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
        private Map<String, String> models = new HashMap<>(); // Alias -> Model ID mapping if needed

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
