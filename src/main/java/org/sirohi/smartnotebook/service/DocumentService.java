package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.UploadResult;
import org.sirohi.smartnotebook.exception.BadRequestException;
import org.sirohi.smartnotebook.exception.DuplicateDocumentException;
import org.sirohi.smartnotebook.model.Document;
import org.sirohi.smartnotebook.repository.DocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Handles document upload, deduplication, and enqueue for processing.
 */
@Service
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepo;
    private final IngestionService ingestionService;
    private final FileStorageService fileStorage;

    public DocumentService(DocumentRepository documentRepo,
            IngestionService ingestionService,
            FileStorageService fileStorage) {
        this.documentRepo = documentRepo;
        this.ingestionService = ingestionService;
        this.fileStorage = fileStorage;
    }

    public UploadResult uploadAndEnqueue(MultipartFile file, String title) {
        // 1. Compute checksum for dedup
        String checksum = computeSha256(file);

        // 2. Check for duplicate
        Optional<Document> existing = documentRepo.findByChecksum(checksum);
        if (existing.isPresent()) {
            throw new DuplicateDocumentException(
                    "Document already exists: " + existing.get().getId());
        }

        // 3. Store file to disk
        String filePath = fileStorage.store(file);

        // 4. Create document record
        Document doc = new Document();
        doc.setTitle(title);
        doc.setContentType(detectContentType(file));
        doc.setFilePath(filePath);
        doc.setFileSizeBytes(file.getSize());
        doc.setChecksum(checksum);
        doc.setStatus("UPLOADED");
        doc = documentRepo.save(doc);

        // 5. Enqueue ingestion task
        UUID taskId = ingestionService.enqueue(doc);

        return new UploadResult(doc.getId(), taskId);
    }

    @Transactional(readOnly = true)
    public Page<Document> listDocuments(int page, int size) {
        return documentRepo.findAll(
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
