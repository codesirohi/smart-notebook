package org.sirohi.smartnotebook.model;

/**
 * Supported LLM Providers.
 */
public enum ModelProvider {
    GOOGLE("google"),
    ANTHROPIC("anthropic"),
    OPENAI("openai"),
    OLLAMA("ollama");

    private final String providerId;

    ModelProvider(String providerId) {
        this.providerId = providerId;
    }

    public String getProviderId() {
        return providerId;
    }

    /**
     * Resolve provider from model name prefix or config.
     * Simple heuristic: "gpt" -> OPENAI, "claude" -> ANTHROPIC, "gemini" -> GOOGLE.
     * Default to OLLAMA.
     */
    public static ModelProvider fromModelName(String modelName) {
        if (modelName == null)
            return OLLAMA;
        String lower = modelName.toLowerCase();
        if (lower.startsWith("gpt") || lower.startsWith("o1"))
            return OPENAI;
        if (lower.startsWith("claude"))
            return ANTHROPIC;
        if (lower.startsWith("gemini"))
            return GOOGLE;
        return OLLAMA;
    }
}
