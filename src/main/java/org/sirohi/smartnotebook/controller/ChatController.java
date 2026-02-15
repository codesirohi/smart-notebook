package org.sirohi.smartnotebook.controller;

import jakarta.validation.Valid;
import org.sirohi.smartnotebook.dto.*;
import org.sirohi.smartnotebook.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // --- Chat lifecycle (scoped to notebook) ---

    @PostMapping("/notebooks/{notebookId}/chats")
    public ResponseEntity<ChatResponse> createChat(
            @PathVariable UUID notebookId,
            @RequestBody(required = false) ChatRequest request) {
        ChatRequest req = request != null ? request : new ChatRequest(null);
        ChatResponse chat = chatService.createChat(notebookId, req);
        return ResponseEntity
                .created(URI.create("/api/chats/" + chat.id()))
                .body(chat);
    }

    @GetMapping("/notebooks/{notebookId}/chats")
    public ResponseEntity<List<ChatResponse>> listChats(@PathVariable UUID notebookId) {
        return ResponseEntity.ok(chatService.listChats(notebookId));
    }

    // --- Chat operations ---

    @GetMapping("/chats/{chatId}")
    public ResponseEntity<ChatResponse> getChat(@PathVariable UUID chatId) {
        return ResponseEntity.ok(chatService.getChatWithHistory(chatId));
    }

    @PostMapping("/chats/{chatId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable UUID chatId,
            @Valid @RequestBody ChatMessageRequest request) {
        ChatMessageResponse response = chatService.sendMessage(chatId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/chats/{chatId}")
    public ResponseEntity<Void> deleteChat(@PathVariable UUID chatId) {
        chatService.deleteChat(chatId);
        return ResponseEntity.noContent().build();
    }
}
