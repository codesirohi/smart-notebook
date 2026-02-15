package org.sirohi.smartnotebook.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotebookResponse(
        UUID id,
        String name,
        String description,
        long documentCount,
        long chatCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
