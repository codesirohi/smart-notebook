package org.sirohi.smartnotebook.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sirohi.smartnotebook.domain.Document;
import org.sirohi.smartnotebook.domain.DocumentStatus;
import org.sirohi.smartnotebook.queue.MessagePublisher;
import org.sirohi.smartnotebook.source.dto.UploadRequest;
import org.sirohi.smartnotebook.source.dto.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service handling document upload, deduplication (content_hash), and ingestion
 * enqueuing.
 *
 * <p>
 * Flow: validate → dedup check → persist → enqueue for ingestion.
 * </p>
 */
@Service
public class SourceService {

    private static final Logger log = LoggerFactory.getLogger(SourceService.class);

    private final SourceRepository sourceRepository;
    private final MessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;

    public SourceService(SourceRepository sourceRepository,
            MessagePublisher messagePublisher,
            ObjectMapper objectMapper) {
        this.sourceRepository = sourceRepository;
        this.messagePublisher = messagePublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles document upload: dedup, persist, and enqueue for processing.
     *
     * @param request validated upload request with file metadata
     * @return response with document ID and status
     */
    @Transactional
    public UploadResponse upload(UploadRequest request) {
        // 1. Dedup check — same file already uploaded?
        Optional<Document> existing = sourceRepository.findByContentHash(request.contentHash());
        if (existing.isPresent()) {
            Document doc = existing.get();
            log.info("Duplicate detected for file '{}' (hash={}), existing doc={}",
                    request.filename(), request.contentHash(), doc.getId());
            return UploadResponse.duplicate(doc.getId(), doc.getStatus().name());
        }

        // 2. Persist new document (status = PENDING)
        Document document = new Document(
                request.filename(),
                request.mimeType(),
                request.contentHash(),
                request.fileSizeBytes());
        document = sourceRepository.save(document);
        log.info("Saved new document '{}' with id={}", request.filename(), document.getId());

        // 3. Enqueue ingestion request for the Python worker
        enqueueIngestion(document);

        return UploadResponse.created(document.getId());
    }

    /**
     * Returns the current status of a document.
     *
     * @param documentId the document UUID
     * @return the document if found
     */
    @Transactional(readOnly = true)
    public Optional<Document> getStatus(UUID documentId) {
        return sourceRepository.findById(documentId);
    }

    private void enqueueIngestion(Document document) {
        try {
            Map<String, Object> payload = Map.of(
                    "documentId", document.getId().toString(),
                    "filename", document.getFilename(),
                    "mimeType", document.getMimeType(),
                    "fileSizeBytes", document.getFileSizeBytes() != null ? document.getFileSizeBytes() : 0);
            String json = objectMapper.writeValueAsString(payload);
            messagePublisher.publish(document.getId().toString(), json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ingestion request for document {}", document.getId(), e);
            throw new RuntimeException("Failed to enqueue document for processing", e);
        }
    }
}
