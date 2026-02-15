package org.sirohi.smartnotebook.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID id,
        UUID notebookId,
        String title,
        List<ChatMessageResponse> messages,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
