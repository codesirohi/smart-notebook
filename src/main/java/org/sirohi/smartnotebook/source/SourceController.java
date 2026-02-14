package org.sirohi.smartnotebook.source;

import org.sirohi.smartnotebook.source.dto.UploadRequest;
import org.sirohi.smartnotebook.source.dto.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for document upload and status checking.
 *
 * <p>
 * Endpoints:
 * <ul>
 * <li>{@code POST /api/sources/upload} — upload a document (multipart
 * form)</li>
 * <li>{@code GET /api/sources/{id}/status} — check processing status</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private static final Logger log = LoggerFactory.getLogger(SourceController.class);

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    /**
     * Upload a document for ingestion.
     * Accepts multipart file upload, computes SHA-256 hash for dedup, and enqueues
     * for processing.
     *
     * @param file the uploaded file
     * @return 202 Accepted with document ID, or 200 if duplicate
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new UploadResponse(null, "ERROR", "File is empty", false));
        }

        try {
            // Compute SHA-256 content hash for dedup
            byte[] content = file.getBytes();
            String contentHash = computeSha256(content);

            UploadRequest request = new UploadRequest(
                    file.getOriginalFilename(),
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                    contentHash,
                    file.getSize(),
                    null // temp file path not needed — content already in memory for small files
            );

            UploadResponse response = sourceService.upload(request);

            if (response.duplicate()) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (IOException e) {
            log.error("Failed to read uploaded file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new UploadResponse(null, "ERROR", "Failed to process file upload", false));
        }
    }

    /**
     * Check the processing status of a document.
     *
     * @param id the document UUID
     * @return document status or 404
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable UUID id) {
        return sourceService.getStatus(id)
                .map(doc -> ResponseEntity.ok(Map.<String, Object>of(
                        "documentId", doc.getId(),
                        "filename", doc.getFilename(),
                        "status", doc.getStatus().name(),
                        "uploadedAt", doc.getUploadedAt().toString())))
                .orElse(ResponseEntity.notFound().build());
    }

    private String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
