package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.model.EmbeddingCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for query embedding cache operations.
 */
@Repository
public interface EmbeddingCacheRepository extends JpaRepository<EmbeddingCache, UUID> {

    /**
     * Find cached embedding by query hash and model.
     */
    Optional<EmbeddingCache> findByQueryHashAndEmbeddingModel(String queryHash, String embeddingModel);

    /**
     * Increment hit count and update last_used_at atomically.
     */
    @Modifying
    @Query("""
        UPDATE EmbeddingCache e
        SET e.hitCount = e.hitCount + 1, e.lastUsedAt = CURRENT_TIMESTAMP
        WHERE e.id = :id
        """)
    void recordHit(@Param("id") UUID id);

    /**
     * Delete cache entries older than the given threshold (TTL cleanup).
     */
    @Modifying
    @Query("DELETE FROM EmbeddingCache e WHERE e.lastUsedAt < :threshold")
    int deleteOlderThan(@Param("threshold") OffsetDateTime threshold);

    /**
     * Count total cache entries (for monitoring).
     */
    @Query("SELECT COUNT(e) FROM EmbeddingCache e")
    long countEntries();

    /**
     * Sum of all hit counts (for monitoring cache effectiveness).
     */
    @Query("SELECT COALESCE(SUM(e.hitCount), 0) FROM EmbeddingCache e")
    long sumHitCount();
}
