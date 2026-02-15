package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.*;
import org.sirohi.smartnotebook.exception.ResourceNotFoundException;
import org.sirohi.smartnotebook.model.Chat;
import org.sirohi.smartnotebook.model.ChatMessage;
import org.sirohi.smartnotebook.model.Notebook;
import org.sirohi.smartnotebook.repository.ChatMessageRepository;
import org.sirohi.smartnotebook.repository.ChatRepository;
import org.sirohi.smartnotebook.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ChatService {

        private final ChatRepository chatRepo;
        private final ChatMessageRepository messageRepo;
        private final DocumentRepository documentRepo;
        private final QueryService queryService;
        private final NotebookService notebookService;

        @Value("${app.chat.history-window:10}")
        private int historyWindow;

        public ChatService(ChatRepository chatRepo,
                        ChatMessageRepository messageRepo,
                        DocumentRepository documentRepo,
                        QueryService queryService,
                        NotebookService notebookService) {
                this.chatRepo = chatRepo;
                this.messageRepo = messageRepo;
                this.documentRepo = documentRepo;
                this.queryService = queryService;
                this.notebookService = notebookService;
        }

        public ChatResponse createChat(UUID notebookId, ChatRequest request) {
                Notebook notebook = notebookService.getEntityById(notebookId);
                Chat chat = new Chat();
                chat.setNotebook(notebook);
                chat.setTitle(request.title() != null ? request.title() : "New Chat");
                chat = chatRepo.save(chat);
                return toChatResponse(chat, List.of());
        }

        @Transactional(readOnly = true)
        public List<ChatResponse> listChats(UUID notebookId) {
                List<Chat> chats = chatRepo.findByNotebookId(notebookId,
                                Sort.by(Sort.Direction.DESC, "updatedAt"));
                return chats.stream()
                                .map(c -> toChatResponse(c, List.of()))
                                .toList();
        }

        @Transactional(readOnly = true)
        public ChatResponse getChatWithHistory(UUID chatId) {
                Chat chat = chatRepo.findById(chatId)
                                .orElseThrow(() -> new ResourceNotFoundException("Chat not found: " + chatId));
                List<ChatMessage> messages = messageRepo.findByChatIdOrderByCreatedAtAsc(chatId);
                return toChatResponse(chat, messages);
        }

        /**
         * Send a user message to a chat. This triggers the RAG pipeline scoped to the
         * notebook's documents and includes conversation history for context.
         */
        public ChatMessageResponse sendMessage(UUID chatId, ChatMessageRequest request) {
                Chat chat = chatRepo.findById(chatId)
                                .orElseThrow(() -> new ResourceNotFoundException("Chat not found: " + chatId));

                // 1. Save the user message
                ChatMessage userMessage = new ChatMessage();
                userMessage.setChat(chat);
                userMessage.setRole("user");
                userMessage.setContent(request.content());
                messageRepo.save(userMessage);

                // 2. Load recent conversation history (last K messages for context)
                List<ChatMessage> recentHistory = messageRepo.findByChatIdOrderByCreatedAtDesc(
                                chatId, PageRequest.of(0, historyWindow));
                // Reverse to chronological order
                List<ChatMessage> historyChronological = new ArrayList<>(recentHistory);
                Collections.reverse(historyChronological);

                // 3. Get document IDs scoped to this notebook
                UUID notebookId = chat.getNotebook().getId();
                List<UUID> notebookDocIds = documentRepo.findIdsByNotebookId(notebookId);

                // 4. Run conversation-aware RAG query
                long startTime = System.currentTimeMillis();
                QueryResponse ragResponse = queryService.queryWithHistory(
                                request.content(),
                                5,
                                notebookDocIds,
                                historyChronological);
                long latencyMs = System.currentTimeMillis() - startTime;

                // 5. Save the assistant response
                ChatMessage assistantMessage = new ChatMessage();
                assistantMessage.setChat(chat);
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(ragResponse.answer());
                assistantMessage.setCitations(citationsToJsonList(ragResponse.citations()));
                assistantMessage.setMetadata(Map.of(
                                "confidence", ragResponse.confidence(),
                                "latencyMs", latencyMs,
                                "citationCount", ragResponse.citations().size()));
                messageRepo.save(assistantMessage);

                // 6. Update chat timestamp
                chat.setUpdatedAt(java.time.OffsetDateTime.now());
                chatRepo.save(chat);

                return toMessageResponse(assistantMessage, ragResponse.citations(),
                                ragResponse.confidence(), latencyMs);
        }

        public void deleteChat(UUID chatId) {
                if (!chatRepo.existsById(chatId)) {
                        throw new ResourceNotFoundException("Chat not found: " + chatId);
                }
                chatRepo.deleteById(chatId);
        }

        /**
         * Stream a chat response as SSE events.
         * Saves user message, runs streaming RAG, emits token events,
         * and persists the final assistant message on completion.
         */
        public reactor.core.publisher.Flux<org.springframework.http.codec.ServerSentEvent<ChatTokenEvent>> sendMessageStreaming(
                        UUID chatId, ChatMessageRequest request) {
                Chat chat = chatRepo.findById(chatId)
                                .orElseThrow(() -> new ResourceNotFoundException("Chat not found: " + chatId));

                // 1. Save the user message
                ChatMessage userMessage = new ChatMessage();
                userMessage.setChat(chat);
                userMessage.setRole("user");
                userMessage.setContent(request.content());
                messageRepo.save(userMessage);

                // 2. Load recent conversation history
                List<ChatMessage> recentHistory = messageRepo.findByChatIdOrderByCreatedAtDesc(
                                chatId, org.springframework.data.domain.PageRequest.of(0, historyWindow));
                List<ChatMessage> historyChronological = new ArrayList<>(recentHistory);
                Collections.reverse(historyChronological);

                // 3. Get document IDs scoped to this notebook
                UUID notebookId = chat.getNotebook().getId();
                List<UUID> notebookDocIds = documentRepo.findIdsByNotebookId(notebookId);

                // 4. Run streaming RAG query
                long startTime = System.currentTimeMillis();
                QueryService.StreamingQueryContext ctx = queryService.queryStreaming(
                                request.content(), 5, notebookDocIds, historyChronological);

                // 5. Build SSE event stream
                StringBuilder fullResponse = new StringBuilder();

                return ctx.tokenStream()
                                .map(token -> {
                                        fullResponse.append(token);
                                        return org.springframework.http.codec.ServerSentEvent.<ChatTokenEvent>builder()
                                                        .event("token")
                                                        .data(ChatTokenEvent.token(token))
                                                        .build();
                                })
                                .concatWith(reactor.core.publisher.Mono.fromCallable(() -> {
                                        long latencyMs = System.currentTimeMillis() - startTime;

                                        // Save assistant message with full response
                                        ChatMessage assistantMessage = new ChatMessage();
                                        assistantMessage.setChat(chat);
                                        assistantMessage.setRole("assistant");
                                        assistantMessage.setContent(fullResponse.toString());
                                        assistantMessage.setCitations(citationsToJsonList(ctx.citations()));
                                        assistantMessage.setMetadata(Map.of(
                                                        "confidence", ctx.confidence(),
                                                        "latencyMs", latencyMs,
                                                        "citationCount", ctx.citations().size()));
                                        messageRepo.save(assistantMessage);

                                        // Update chat timestamp
                                        chat.setUpdatedAt(java.time.OffsetDateTime.now());
                                        chatRepo.save(chat);

                                        return org.springframework.http.codec.ServerSentEvent.<ChatTokenEvent>builder()
                                                        .event("done")
                                                        .data(ChatTokenEvent.done(ctx.citations(), ctx.confidence(),
                                                                        latencyMs))
                                                        .build();
                                }));
        }

        private ChatResponse toChatResponse(Chat chat, List<ChatMessage> messages) {
                List<ChatMessageResponse> messageResponses = messages.stream()
                                .map(this::toMessageResponseFromEntity)
                                .toList();
                return new ChatResponse(
                                chat.getId(),
                                chat.getNotebook().getId(),
                                chat.getTitle(),
                                messageResponses,
                                chat.getCreatedAt(),
                                chat.getUpdatedAt());
        }

        private ChatMessageResponse toMessageResponseFromEntity(ChatMessage msg) {
                List<Citation> citations = List.of();
                double confidence = 0.0;
                long latencyMs = 0;

                if (msg.getMetadata() != null) {
                        Object confObj = msg.getMetadata().get("confidence");
                        if (confObj instanceof Number n)
                                confidence = n.doubleValue();
                        Object latObj = msg.getMetadata().get("latencyMs");
                        if (latObj instanceof Number n)
                                latencyMs = n.longValue();
                }

                // Re-parse stored citations
                if (msg.getCitations() != null && !msg.getCitations().isEmpty()) {
                        citations = msg.getCitations().stream()
                                        .map(c -> new Citation(
                                                        (String) c.getOrDefault("documentTitle", ""),
                                                        c.containsKey("chunkIndex")
                                                                        ? ((Number) c.get("chunkIndex")).intValue()
                                                                        : 0,
                                                        (String) c.getOrDefault("chunkContent", ""),
                                                        c.containsKey("similarity")
                                                                        ? ((Number) c.get("similarity")).doubleValue()
                                                                        : 0.0))
                                        .toList();
                }

                return new ChatMessageResponse(
                                msg.getId(), msg.getRole(), msg.getContent(),
                                citations, confidence, latencyMs, msg.getCreatedAt());
        }

        private ChatMessageResponse toMessageResponse(ChatMessage msg, List<Citation> citations,
                        double confidence, long latencyMs) {
                return new ChatMessageResponse(
                                msg.getId(), msg.getRole(), msg.getContent(),
                                citations, confidence, latencyMs, msg.getCreatedAt());
        }

        private List<Map<String, Object>> citationsToJsonList(List<Citation> citations) {
                return citations.stream()
                                .map(c -> {
                                        Map<String, Object> map = new LinkedHashMap<>();
                                        map.put("documentTitle", c.documentTitle());
                                        map.put("chunkIndex", c.chunkIndex());
                                        map.put("chunkContent", c.chunkContent());
                                        map.put("similarity", c.similarity());
                                        return map;
                                })
                                .toList();
        }
}
