package org.sirohi.smartnotebook.dto;

import java.util.UUID;

public record UploadResponse(UUID documentId, UUID taskId, String message) {
}
