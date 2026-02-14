package org.sirohi.smartnotebook.model.gateway.providers;

import org.sirohi.smartnotebook.domain.ChatRequest;
import org.sirohi.smartnotebook.domain.ChatResponse;
import org.sirohi.smartnotebook.model.gateway.ChatClient;

/**
 * {@link ChatClient} adapter for Anthropic Claude models.
 *
 * <p>
 * Used in the {@code prod} profile for high-quality responses.
 * Provider-specific logic is isolated here — business logic only
 * interacts with {@link ChatClient}.
 * </p>
 */
public class AnthropicChatClient implements ChatClient {

    @Override
    public ChatResponse complete(ChatRequest request) {
        // TODO: implement Anthropic API call via Spring AI AnthropicChatModel
        throw new UnsupportedOperationException("AnthropicChatClient not yet implemented");
    }
}
