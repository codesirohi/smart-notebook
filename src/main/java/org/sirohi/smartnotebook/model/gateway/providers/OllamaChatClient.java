package org.sirohi.smartnotebook.model.gateway.providers;

import org.sirohi.smartnotebook.domain.ChatRequest;
import org.sirohi.smartnotebook.domain.ChatResponse;
import org.sirohi.smartnotebook.model.gateway.ChatClient;

/**
 * {@link ChatClient} adapter for Ollama (local LLM inference).
 *
 * <p>
 * Used in the {@code dev} profile for free, local inference with models
 * like Phi-3 Mini (~2.5 GB RAM).
 * </p>
 */
public class OllamaChatClient implements ChatClient {

    @Override
    public ChatResponse complete(ChatRequest request) {
        // TODO: implement Ollama API call via Spring AI OllamaChatModel
        throw new UnsupportedOperationException("OllamaChatClient not yet implemented");
    }
}
