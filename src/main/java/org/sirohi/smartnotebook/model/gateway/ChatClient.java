package org.sirohi.smartnotebook.model.gateway;

import org.sirohi.smartnotebook.domain.ChatRequest;
import org.sirohi.smartnotebook.domain.ChatResponse;

/**
 * Provider-agnostic interface for LLM chat completion.
 * Implementations: OllamaChatClient, AnthropicChatClient, OpenAIChatClient.
 */
public interface ChatClient {

    ChatResponse complete(ChatRequest request);
}
