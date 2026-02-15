package org.sirohi.smartnotebook.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        String role,
        String content,
        List<Citation> citations,
        double confidence,
        long latencyMs,
        OffsetDateTime createdAt) {
}
