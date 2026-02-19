package org.sirohi.smartnotebook.model;

/**
 * Supported LLM Providers.
 */
public enum ModelProvider {
    GOOGLE("google"),
    ANTHROPIC("anthropic"),
    OPENAI("openai"),
    GROQ("groq"),
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
     * Supports explicit provider-qualified names (e.g. "groq/llama-3.3-70b-versatile").
     * Heuristic fallback: "gpt" -> OPENAI, "claude" -> ANTHROPIC, "gemini" -> GOOGLE.
     * Default to OLLAMA.
     */
    public static ModelProvider fromModelName(String modelName) {
        if (modelName == null)
            return OLLAMA;
        String lower = modelName.trim().toLowerCase();

        // Explicit provider-qualified model names (e.g., "groq/llama-3.3-70b-versatile")
        if (lower.startsWith("openai/") || lower.startsWith("openai:"))
            return OPENAI;
        if (lower.startsWith("anthropic/") || lower.startsWith("anthropic:"))
            return ANTHROPIC;
        if (lower.startsWith("google/") || lower.startsWith("google:"))
            return GOOGLE;
        if (lower.startsWith("groq/") || lower.startsWith("groq:"))
            return GROQ;
        if (lower.startsWith("ollama/") || lower.startsWith("ollama:"))
            return OLLAMA;

        if (lower.startsWith("gpt") || lower.startsWith("o1"))
            return OPENAI;
        if (lower.startsWith("claude"))
            return ANTHROPIC;
        if (lower.startsWith("gemini"))
            return GOOGLE;
        if (lower.startsWith("llama-3.3-70b-versatile")
                || lower.startsWith("llama-3.1-8b-instant")
                || lower.startsWith("mixtral-8x7b-32768")
                || lower.startsWith("gemma2-9b-it")
                || lower.startsWith("deepseek-r1-distill-llama-70b")) {
            return GROQ;
        }
        return OLLAMA;
    }
}
