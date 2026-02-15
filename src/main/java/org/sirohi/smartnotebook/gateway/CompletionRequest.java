package org.sirohi.smartnotebook.gateway;

import java.util.Map;

public record CompletionRequest(
        String systemPrompt,
        String userPrompt,
        String model,
        Map<String, Object> parameters) {
    public CompletionRequest(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, null, Map.of());
    }

    public CompletionRequest(String systemPrompt, String userPrompt, String model) {
        this(systemPrompt, userPrompt, model, Map.of());
    }
}
