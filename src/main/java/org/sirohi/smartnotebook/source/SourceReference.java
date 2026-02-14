package org.sirohi.smartnotebook.source;

/**
 * Identifies a document source and its type.
 * Used by {@link FileSource} implementations to resolve how to fetch content.
 *
 * @param sourceType the type of source — "local", "s3", "gdrive", etc.
 * @param location   source-specific location — file path, S3 URI, Google Drive
 *                   file ID, etc.
 * @param filename   original filename for display and mime-type detection
 * @param metadata   optional key-value metadata (e.g., bucket name, folder ID)
 */
public record SourceReference(
        String sourceType,
        String location,
        String filename,
        java.util.Map<String, String> metadata) {
    /**
     * Convenience constructor for local file uploads (most common case).
     */
    public static SourceReference local(String filename, String tempPath) {
        return new SourceReference("local", tempPath, filename, java.util.Map.of());
    }
}
