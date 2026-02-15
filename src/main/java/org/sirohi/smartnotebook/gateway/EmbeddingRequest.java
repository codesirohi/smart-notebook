package org.sirohi.smartnotebook.gateway;

public record EmbeddingRequest(
        String text,
        String model) {
    public EmbeddingRequest(String text) {
        this(text, null);
    }
}
