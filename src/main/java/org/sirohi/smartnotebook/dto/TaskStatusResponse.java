package org.sirohi.smartnotebook.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TaskStatusResponse(
        UUID taskId,
        UUID documentId,
        String status,
        Map<String, Object> result,
        String errorMessage,
        Map<String, Object> errorDetails,
        int retryCount,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
