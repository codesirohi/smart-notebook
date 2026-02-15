package org.sirohi.smartnotebook.gateway;

/**
 * Provider-agnostic model gateway interface.
 * Decouples business logic from LLM providers.
 * Phase 1: OllamaModelGateway. Phase 2: Anthropic, OpenAI.
 */
public interface ModelGateway {

    /**
     * Generate text completion from a prompt.
     * Used for RAG answer generation.
     */
    CompletionResponse complete(CompletionRequest request);

    /**
     * Stream text completion token-by-token.
     * Returns a Flux that emits individual tokens as they are generated.
     * Default implementation falls back to non-streaming complete().
     */
    default reactor.core.publisher.Flux<String> completeStreaming(CompletionRequest request) {
        CompletionResponse response = complete(request);
        return reactor.core.publisher.Flux.just(response.text());
    }

    /**
     * Generate vector embedding for text.
     * Used for document chunks and query embedding.
     */
    EmbeddingResponse embed(EmbeddingRequest request);

    /**
     * Check model provider health and availability.
     */
    ModelHealth health();

    /**
     * Returns the provider identifier (e.g., "ollama", "anthropic", "openai").
     */
    String providerId();
}
