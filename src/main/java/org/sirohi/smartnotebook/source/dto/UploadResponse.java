package org.sirohi.smartnotebook.source.dto;

import java.util.UUID;

/**
 * Response DTO for document upload (returns 202 Accepted with document ID).
 *
 * @param documentId ID of the created/existing document
 * @param status     current processing status (PENDING, PROCESSING, READY,
 *                   FAILED)
 * @param message    human-readable status message
 * @param duplicate  true if the file was already uploaded (dedup match)
 */
public record UploadResponse(
        UUID documentId,
        String status,
        String message,
        boolean duplicate) {
    /**
     * Factory for a newly uploaded document.
     */
    public static UploadResponse created(UUID documentId) {
        return new UploadResponse(documentId, "PENDING", "Document accepted for processing", false);
    }

    /**
     * Factory for a duplicate document.
     */
    public static UploadResponse duplicate(UUID documentId, String currentStatus) {
        return new UploadResponse(documentId, currentStatus,
                "Document already uploaded", true);
    }
}
