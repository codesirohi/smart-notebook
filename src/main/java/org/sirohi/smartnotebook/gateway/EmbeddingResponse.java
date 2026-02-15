package org.sirohi.smartnotebook.gateway;

public record EmbeddingResponse(
        float[] vector,
        int dimensions,
        long latencyMs,
        String modelUsed) {
}
