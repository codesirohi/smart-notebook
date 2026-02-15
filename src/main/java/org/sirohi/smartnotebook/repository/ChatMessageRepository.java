package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByChatIdOrderByCreatedAtAsc(UUID chatId);

    List<ChatMessage> findByChatIdOrderByCreatedAtDesc(UUID chatId, Pageable pageable);
}
