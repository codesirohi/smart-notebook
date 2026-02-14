package org.sirohi.smartnotebook.model.gateway;

import java.util.List;

/**
 * Provider-agnostic interface for text embedding.
 * Implementations: local (MiniLM) or API-based (OpenAI).
 */
public interface EmbeddingClient {

    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);
}
