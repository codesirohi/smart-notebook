package org.sirohi.smartnotebook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Stores uploaded files to the local filesystem.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
            log.info("Upload directory: {}", this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + this.uploadDir, e);
        }
    }

    /**
     * Store a file and return the path relative to the upload directory.
     */
    public String store(MultipartFile file) {
        String filename = UUID.randomUUID() + "-" + sanitizeFilename(file.getOriginalFilename());
        Path target = uploadDir.resolve(filename);

        try {
            Files.copy(file.getInputStream(), target);
            log.info("Stored file: {}", target);
            return target.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + filename, e);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null)
            return "unknown";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
