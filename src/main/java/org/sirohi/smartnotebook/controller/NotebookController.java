package org.sirohi.smartnotebook.controller;

import jakarta.validation.Valid;
import org.sirohi.smartnotebook.dto.NotebookRequest;
import org.sirohi.smartnotebook.dto.NotebookResponse;
import org.sirohi.smartnotebook.dto.UploadResponse;
import org.sirohi.smartnotebook.dto.UploadResult;
import org.sirohi.smartnotebook.exception.BadRequestException;
import org.sirohi.smartnotebook.model.Document;
import org.sirohi.smartnotebook.service.DocumentService;
import org.sirohi.smartnotebook.service.NotebookService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/notebooks")
public class NotebookController {

    private final NotebookService notebookService;
    private final DocumentService documentService;

    public NotebookController(NotebookService notebookService,
            DocumentService documentService) {
        this.notebookService = notebookService;
        this.documentService = documentService;
    }

    // --- Notebook CRUD ---

    @PostMapping
    public ResponseEntity<NotebookResponse> createNotebook(
            @Valid @RequestBody NotebookRequest request) {
        NotebookResponse response = notebookService.create(request);
        return ResponseEntity
                .created(URI.create("/api/notebooks/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<NotebookResponse>> listNotebooks() {
        return ResponseEntity.ok(notebookService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotebookResponse> getNotebook(@PathVariable UUID id) {
        return ResponseEntity.ok(notebookService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotebookResponse> updateNotebook(
            @PathVariable UUID id,
            @Valid @RequestBody NotebookRequest request) {
        return ResponseEntity.ok(notebookService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotebook(@PathVariable UUID id) {
        notebookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Documents scoped to Notebook ---

    @PostMapping("/{id}/documents/upload")
    public ResponseEntity<UploadResponse> uploadToNotebook(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title) {

        validateFile(file);
        UploadResult result = documentService.uploadAndEnqueue(file, title, id);

        return ResponseEntity
                .accepted()
                .body(new UploadResponse(
                        result.documentId(),
                        result.taskId(),
                        "Document accepted for processing in notebook"));
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<Map<String, Object>> listNotebookDocuments(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Document> docs = documentService.listDocumentsByNotebook(id, page, size);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("documents", docs.getContent());
        response.put("totalElements", docs.getTotalElements());
        response.put("totalPages", docs.getTotalPages());
        response.put("currentPage", docs.getNumber());

        return ResponseEntity.ok(response);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        long maxSize = 50 * 1024 * 1024;
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
