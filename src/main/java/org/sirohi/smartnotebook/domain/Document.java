package org.sirohi.smartnotebook.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an uploaded document.
 * Maps to the 'documents' table.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "content_hash", nullable = false, length = 64, unique = true)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message")
    private String errorMessage;

    protected Document() {
        // JPA
    }

    public Document(String filename, String mimeType, String contentHash, Long fileSizeBytes) {
        this.filename = filename;
        this.mimeType = mimeType;
        this.contentHash = contentHash;
        this.fileSizeBytes = fileSizeBytes;
    }

    // --- Getters ---

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getContentHash() {
        return contentHash;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    // --- Status transitions ---

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
    }

    public void markReady() {
        this.status = DocumentStatus.READY;
        this.processedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = DocumentStatus.FAILED;
        this.processedAt = Instant.now();
        this.errorMessage = error;
    }
}
