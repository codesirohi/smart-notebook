package org.sirohi.smartnotebook.controller;

import org.sirohi.smartnotebook.dto.TaskStatusResponse;
import org.sirohi.smartnotebook.dto.UploadResponse;
import org.sirohi.smartnotebook.dto.UploadResult;
import org.sirohi.smartnotebook.exception.BadRequestException;
import org.sirohi.smartnotebook.model.Document;
import org.sirohi.smartnotebook.service.DocumentService;
import org.sirohi.smartnotebook.service.IngestionService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class DocumentController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;
    private final IngestionService ingestionService;

    public DocumentController(DocumentService documentService,
            IngestionService ingestionService) {
        this.documentService = documentService;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/documents/upload")
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "notebookId", required = false) UUID notebookId,
            @RequestParam(value = "extractionModel", required = false) String extractionModel,
            @RequestParam(value = "embeddingModel", required = false) String embeddingModel) {

        log.info("Received upload request for file: {}, size: {}, title: {}", file.getOriginalFilename(),
                file.getSize(), title);

        // Validate file
        validateFile(file);

        // Store document + enqueue task
        UploadResult result = documentService.uploadAndEnqueue(file, title,
                notebookId,
                extractionModel, embeddingModel);

        log.info("Document accepted. ID: {}, Task ID: {}", result.documentId(), result.taskId());

        return ResponseEntity
                .accepted()
                .body(new UploadResponse(
                        result.documentId(),
                        result.taskId(),
                        "Document accepted for processing"));
    }

    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.debug("Listing documents. Page: {}, Size: {}", page, size);

        Page<Document> docs = documentService.listDocuments(page, size);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("documents", docs.getContent());
        response.put("content", docs.getContent()); // Alias for standard Spring Page
        response.put("totalElements", docs.getTotalElements());
        response.put("totalPages", docs.getTotalPages());
        response.put("currentPage", docs.getNumber());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<Document> getDocument(@PathVariable UUID id) {
        log.debug("Retrieving document: {}", id);
        return documentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tasks/{taskId}/status")
    public ResponseEntity<TaskStatusResponse> getTaskStatus(@PathVariable UUID taskId) {
        // log.debug("Checking task status: {}", taskId); // Verbose, maybe debug only
        return ingestionService.getStatus(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Backward-compatible alias for older clients polling /api/tasks/{taskId}.
     * Canonical endpoint remains /api/tasks/{taskId}/status.
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<TaskStatusResponse> getTaskStatusLegacy(@PathVariable UUID taskId) {
        return getTaskStatus(taskId);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        long maxSize = 50 * 1024 * 1024; // 50MB
        if (file.getSize() > maxSize) {
            throw new BadRequestException("File exceeds 50MB limit");
        }
        String contentType = file.getContentType();
        Set<String> allowed = Set.of(
                "application/pdf",
                "text/plain",
                "text/markdown");
        if (contentType == null || !allowed.contains(contentType)) {
            throw new BadRequestException("Unsupported file type: " + contentType);
        }
    }
}
