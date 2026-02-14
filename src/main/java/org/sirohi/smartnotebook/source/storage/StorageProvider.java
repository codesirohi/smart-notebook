package org.sirohi.smartnotebook.source.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Strategy interface for storing uploaded files.
 * Implementations handle the specifics of where files are stored (local
 * filesystem, S3, etc.).
 */
public interface StorageProvider {

    /**
     * Stores the given file.
     *
     * @param file        the file to store
     * @param contentHash the SHA-256 hash of the content (used for deduplicated
     *                    filenames)
     * @return the location identifier (e.g., file path or S3 key) where the file
     *         was stored
     * @throws IOException if storage fails
     */
    String store(MultipartFile file, String contentHash) throws IOException;
}
