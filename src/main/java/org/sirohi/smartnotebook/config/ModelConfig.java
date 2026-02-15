package org.sirohi.smartnotebook.config;

import jakarta.annotation.PostConstruct;
import org.sirohi.smartnotebook.model.ModelProvider;
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

    private ProviderConfig openai;
    private ProviderConfig anthropic;
    private ProviderConfig google;
    private ProviderConfig ollama;

    private String defaultExtractionModel = "tinyllama";
    private String defaultEmbeddingModel = "all-minilm";

    // Internal map for programmatic access
    private final Map<ModelProvider, ProviderConfig> providers = new HashMap<>();

    @PostConstruct
    public void initProviders() {
        if (openai != null)
            providers.put(ModelProvider.OPENAI, openai);
        if (anthropic != null)
            providers.put(ModelProvider.ANTHROPIC, anthropic);
        if (google != null)
            providers.put(ModelProvider.GOOGLE, google);
        if (ollama != null)
            providers.put(ModelProvider.OLLAMA, ollama);
    }

    public Map<ModelProvider, ProviderConfig> getProviders() {
        return providers;
    }

    // Getters and Setters for binding
    public ProviderConfig getOpenai() {
        return openai;
    }

    public void setOpenai(ProviderConfig openai) {
        this.openai = openai;
    }

    public ProviderConfig getAnthropic() {
        return anthropic;
    }

    public void setAnthropic(ProviderConfig anthropic) {
        this.anthropic = anthropic;
    }

    public ProviderConfig getGoogle() {
        return google;
    }

    public void setGoogle(ProviderConfig google) {
        this.google = google;
    }

    public ProviderConfig getOllama() {
        return ollama;
    }

    public void setOllama(ProviderConfig ollama) {
        this.ollama = ollama;
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
