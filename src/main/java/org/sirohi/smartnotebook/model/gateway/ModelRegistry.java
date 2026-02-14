package org.sirohi.smartnotebook.model.gateway;

/**
 * Registry for resolving ChatClient and EmbeddingClient by model tier.
 * Config-driven: dev profile → Ollama, prod profile → Claude/GPT.
 */
public interface ModelRegistry {

    ChatClient getChatClient(String modelTier);

    EmbeddingClient getEmbeddingClient();
}
