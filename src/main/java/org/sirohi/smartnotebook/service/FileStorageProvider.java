package org.sirohi.smartnotebook.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Interface for file storage backend.
 */
public interface FileStorageProvider {
    /**
     * Store a file and return the path, URL, or identifier.
     *
     * @param file The file to store
     * @return The storage reference (path/id/url)
     */
    String store(MultipartFile file);
}
