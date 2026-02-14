package org.sirohi.smartnotebook.model.gateway.providers;

import org.sirohi.smartnotebook.domain.ChatRequest;
import org.sirohi.smartnotebook.domain.ChatResponse;
import org.sirohi.smartnotebook.model.gateway.ChatClient;

/**
 * {@link ChatClient} adapter for OpenAI models (GPT-4o-mini, etc.).
 *
 * <p>
 * Used in the {@code prod} profile as a budget-tier or fallback model.
 * Provider-specific logic is isolated here — business logic only
 * interacts with {@link ChatClient}.
 * </p>
 */
public class OpenAIChatClient implements ChatClient {

    @Override
    public ChatResponse complete(ChatRequest request) {
        // TODO: implement OpenAI API call via Spring AI OpenAiChatModel
        throw new UnsupportedOperationException("OpenAIChatClient not yet implemented");
    }
}
