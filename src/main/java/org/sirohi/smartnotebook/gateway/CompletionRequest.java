package org.sirohi.smartnotebook.gateway;

import java.util.Map;

public record CompletionRequest(
        String systemPrompt,
        String userPrompt,
        Map<String, Object> parameters) {
    public CompletionRequest(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, Map.of());
    }
}
