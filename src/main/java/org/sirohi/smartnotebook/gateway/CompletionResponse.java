package org.sirohi.smartnotebook.gateway;

public record CompletionResponse(
        String text,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        String modelUsed) {
}
