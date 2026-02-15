package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.UploadResult;
import org.sirohi.smartnotebook.exception.DuplicateDocumentException;
import org.sirohi.smartnotebook.model.Document;
import org.sirohi.smartnotebook.model.Notebook;
import org.sirohi.smartnotebook.repository.DocumentRepository;
import org.sirohi.smartnotebook.repository.NotebookRepository;
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
 */
@Service
@Transactional
public class DocumentService {

    private static final UUID DEFAULT_NOTEBOOK_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final DocumentRepository documentRepo;
    private final NotebookRepository notebookRepo;
    private final IngestionService ingestionService;
    private final FileStorageService fileStorage;

    public DocumentService(DocumentRepository documentRepo,
            NotebookRepository notebookRepo,
            IngestionService ingestionService,
            FileStorageService fileStorage) {
        this.documentRepo = documentRepo;
        this.notebookRepo = notebookRepo;
        this.ingestionService = ingestionService;
        this.fileStorage = fileStorage;
    }

    public UploadResult uploadAndEnqueue(MultipartFile file, String title) {
        return uploadAndEnqueue(file, title, DEFAULT_NOTEBOOK_ID);
    }

    public UploadResult uploadAndEnqueue(MultipartFile file, String title, UUID notebookId) {
        // 1. Compute checksum for dedup
        String checksum = computeSha256(file);

        // 2. Check for duplicate
        Optional<Document> existing = documentRepo.findByChecksum(checksum);
        if (existing.isPresent()) {
            throw new DuplicateDocumentException(
                    "Document already exists: " + existing.get().getId());
        }

        // 3. Resolve notebook
        Notebook notebook = notebookRepo.findById(notebookId)
                .orElseThrow(() -> new org.sirohi.smartnotebook.exception.ResourceNotFoundException(
                        "Notebook not found: " + notebookId));

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
        doc = documentRepo.save(doc);

        // 6. Enqueue ingestion task
        UUID taskId = ingestionService.enqueue(doc);

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
