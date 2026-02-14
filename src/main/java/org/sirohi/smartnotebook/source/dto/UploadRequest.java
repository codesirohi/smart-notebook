package org.sirohi.smartnotebook.source.dto;

/**
 * Request DTO for document upload.
 * Not directly deserialized from JSON — the controller builds this from the
 * multipart form.
 *
 * @param filename      original filename of the uploaded file
 * @param mimeType      detected MIME type (e.g., "application/pdf")
 * @param contentHash   SHA-256 hex digest of the file content (for dedup)
 * @param fileSizeBytes size of the file in bytes
 * @param tempFilePath  path to the temporary file on disk
 */
public record UploadRequest(
        String filename,
        String mimeType,
        String contentHash,
        long fileSizeBytes,
        String tempFilePath) {
}
