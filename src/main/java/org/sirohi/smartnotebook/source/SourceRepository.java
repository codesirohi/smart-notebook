package org.sirohi.smartnotebook.source;

import org.sirohi.smartnotebook.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Document entity persistence.
 */
@Repository
public interface SourceRepository extends JpaRepository<Document, UUID> {

    /**
     * Find a document by its content hash (SHA-256).
     * Used for deduplication — prevents re-uploading the same file.
     */
    Optional<Document> findByContentHash(String contentHash);
}
