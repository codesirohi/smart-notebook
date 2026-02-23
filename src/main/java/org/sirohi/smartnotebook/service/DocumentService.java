package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.UploadResult;
import org.sirohi.smartnotebook.exception.DuplicateDocumentException;
import org.sirohi.smartnotebook.logging.StructuredLogger;
import org.sirohi.smartnotebook.model.Document;
import org.sirohi.smartnotebook.model.Notebook;
import org.sirohi.smartnotebook.repository.DocumentRepository;
import org.sirohi.smartnotebook.repository.NotebookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles document upload, deduplication, and enqueue for processing.
 *
 * Interview Note: Structured logging tracks document lifecycle events
 * for debugging, monitoring, and audit trails.
 */
@Service
@Transactional
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final UUID DEFAULT_NOTEBOOK_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final DocumentRepository documentRepo;
    private final NotebookRepository notebookRepo;
    private final IngestionService ingestionService;
    private final FileStorageProvider fileStorage;

    public DocumentService(DocumentRepository documentRepo,
            NotebookRepository notebookRepo,
            IngestionService ingestionService,
            FileStorageProvider fileStorage) {
        this.documentRepo = documentRepo;
        this.notebookRepo = notebookRepo;
        this.ingestionService = ingestionService;
        this.fileStorage = fileStorage;
    }

    public UploadResult uploadAndEnqueue(MultipartFile file, String title) {
        return uploadAndEnqueue(file, title, DEFAULT_NOTEBOOK_ID, null, null);
    }

    public UploadResult uploadAndEnqueue(MultipartFile file, String title, UUID notebookId) {
        return uploadAndEnqueue(file, title, notebookId, null, null);
    }

    public UploadResult uploadAndEnqueue(MultipartFile file, String title, UUID notebookId,
            String extractionModel, String embeddingModel) {
        long startTime = System.currentTimeMillis();

        StructuredLogger.info(log, "document_upload_started")
                .field("filename", file.getOriginalFilename())
                .field("title", title)
                .field("file_size_bytes", file.getSize())
                .field("content_type", file.getContentType())
                .field("notebook_id", notebookId)
                .log();

        // 1. Compute checksum for dedup
        String checksum = computeSha256(file);

        // 2. Check for duplicate
        Optional<Document> existing = documentRepo.findByChecksum(checksum);
        if (existing.isPresent()) {
            // Interview Note: Deduplication prevents wasting resources on duplicate
            // processing
            StructuredLogger.warn(log, "duplicate_document_detected")
                    .field("checksum", checksum)
                    .field("existing_document_id", existing.get().getId())
                    .field("filename", file.getOriginalFilename())
                    .log();

            throw new DuplicateDocumentException(
                    "Document already exists: " + existing.get().getId());
        }

        // 3. Resolve notebook
        Notebook notebook;
        if (notebookId != null) {
            notebook = notebookRepo.findById(notebookId)
                    .orElseThrow(() -> new org.sirohi.smartnotebook.exception.ResourceNotFoundException(
                            "Notebook not found: " + notebookId));
        } else {
            // Fallback: Default ID -> First Available -> Create New
            notebook = notebookRepo.findById(DEFAULT_NOTEBOOK_ID)
                    .or(() -> notebookRepo.findFirstByOrderByCreatedAtAsc())
                    .orElseGet(() -> {
                        Notebook newNotebook = new Notebook();
                        newNotebook.setName("Default");
                        newNotebook.setDescription("Default notebook for unscoped documents");
                        return notebookRepo.save(newNotebook);
                    });
        }

        // 4. Store file to disk
        String filePath = fileStorage.store(file);

        // 5. Create document record
        Document doc = new Document();
        doc.setTitle(title);
        doc.setContentType(detectContentType(file));
        doc.setFilePath(filePath);
        doc.setFileSizeBytes(file.getSize());
        doc.setChecksum(checksum);
        doc.setNotebook(notebook);
        doc.setStatus("UPLOADED");
        doc = documentRepo.saveAndFlush(doc);

        StructuredLogger.info(log, "document_created")
                .field("document_id", doc.getId())
                .field("notebook_id", notebook.getId())
                .field("content_type", doc.getContentType())
                .field("checksum", checksum)
                .log();

        // 6. Enqueue ingestion task with config overrides
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        if (extractionModel != null)
            config.put("extraction_model", extractionModel);
        if (embeddingModel != null)
            config.put("embedding_model", embeddingModel);

        UUID taskId = ingestionService.enqueue(doc, config);

        long durationMs = System.currentTimeMillis() - startTime;

        // Interview Note: End-to-end upload metrics help identify bottlenecks
        StructuredLogger.info(log, "document_upload_completed")
                .field("document_id", doc.getId())
                .field("task_id", taskId)
                .field("duration_ms", durationMs)
                .field("file_size_bytes", file.getSize())
                .field("extraction_model", extractionModel)
                .field("embedding_model", embeddingModel)
                .log();

        return new UploadResult(doc.getId(), taskId);
    }

    @Transactional(readOnly = true)
    public Page<Document> listDocuments(int page, int size) {
        return documentRepo.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public Page<Document> listDocumentsByNotebook(UUID notebookId, int page, int size) {
        return documentRepo.findByNotebookId(notebookId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public Optional<Document> findById(UUID id) {
        return documentRepo.findById(id);
    }

    private String detectContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null)
            return "text";

        return switch (contentType) {
            case "application/pdf" -> "pdf";
            case "text/markdown" -> "markdown";
            default -> "text";
        };
    }

    private String computeSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute checksum", e);
        }
    }
}
