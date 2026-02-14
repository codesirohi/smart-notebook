package org.sirohi.smartnotebook.source.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem implementation of {@link StorageProvider}.
 * Stores files in a local directory, named by their content hash to ensure
 * deduplication.
 */
@Service
public class LocalFileSystemStorageProvider implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemStorageProvider.class);

    private final Path storageRoot;

    public LocalFileSystemStorageProvider(@Value("${smart-notebook.storage.local-root:uploads}") String storageRoot) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageRoot);
        } catch (IOException e) {
            throw new RuntimeException("Could not create storage directory: " + this.storageRoot, e);
        }
    }

    @Override
    public String store(MultipartFile file, String contentHash) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // Filename: <hash><extension>
        String filename = contentHash + extension;
        Path targetPath = this.storageRoot.resolve(filename);

        // If file exists (dedup), we just return the path
        if (Files.exists(targetPath)) {
            log.debug("File already exists at {}, skipping write.", targetPath);
            return targetPath.toString();
        }

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Stored file at {}", targetPath);
        return targetPath.toString();
    }
}
