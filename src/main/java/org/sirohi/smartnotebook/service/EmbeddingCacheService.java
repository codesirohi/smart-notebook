package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.logging.StructuredLogger;
import org.sirohi.smartnotebook.repository.EmbeddingCacheRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for caching query embeddings in PostgreSQL.
 * Saves 100-240ms per cache hit by avoiding redundant embedding API calls.
 *
 * Cache key: SHA-256(normalized_query) + embedding_model
 * TTL: 7 days of inactivity (configurable)
 */
@Service
public class EmbeddingCacheService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmbeddingCacheService.class);

    private final JdbcTemplate jdbc;
    private final EmbeddingCacheRepository cacheRepository;

    // TTL: entries unused for 7 days are eligible for cleanup
    private static final int TTL_DAYS = 7;

    public EmbeddingCacheService(JdbcTemplate jdbc, EmbeddingCacheRepository cacheRepository) {
        this.jdbc = jdbc;
        this.cacheRepository = cacheRepository;
    }

    /**
     * Result of a cache lookup.
     */
    public record CacheResult(float[] embedding, boolean wasHit, long latencyMs) {}

    /**
     * Get embedding from cache, or return empty if not cached.
     */
    @Transactional
    public Optional<CacheResult> get(String queryText, String embeddingModel) {
        long startTime = System.currentTimeMillis();
        String queryHash = hashQuery(queryText);

        String sql = """
            SELECT id, embedding::text
            FROM query_embedding_cache
            WHERE query_hash = ? AND embedding_model = ?
            """;

        List<CacheHit> results = jdbc.query(sql, (rs, rowNum) -> {
            UUID id = rs.getObject("id", UUID.class);
            String embeddingStr = rs.getString("embedding");
            return new CacheHit(id, embeddingStr);
        }, queryHash, embeddingModel);

        if (results.isEmpty()) {
            long latencyMs = System.currentTimeMillis() - startTime;
            StructuredLogger.debug(log, "embedding_cache_miss")
                .field("query_hash", queryHash.substring(0, 8))
                .field("model", embeddingModel)
                .field("latency_ms", latencyMs)
                .log();
            return Optional.empty();
        }

        CacheHit hit = results.get(0);

        // Update hit count asynchronously (fire-and-forget)
        cacheRepository.recordHit(hit.id);

        float[] embedding = parseVectorString(hit.embeddingStr);
        long latencyMs = System.currentTimeMillis() - startTime;

        StructuredLogger.info(log, "embedding_cache_hit")
            .field("query_hash", queryHash.substring(0, 8))
            .field("model", embeddingModel)
            .field("latency_ms", latencyMs)
            .log();

        return Optional.of(new CacheResult(embedding, true, latencyMs));
    }

    /**
     * Store embedding in cache.
     */
    @Transactional
    public void put(String queryText, String embeddingModel, float[] embedding) {
        String queryHash = hashQuery(queryText);
        String vectorStr = arrayToVectorString(embedding);

        String sql = """
            INSERT INTO query_embedding_cache (query_hash, query_text, embedding_model, embedding)
            VALUES (?, ?, ?, ?::vector)
            ON CONFLICT (query_hash, embedding_model)
            DO UPDATE SET
                embedding = EXCLUDED.embedding,
                last_used_at = NOW()
            """;

        jdbc.update(sql, queryHash, queryText, embeddingModel, vectorStr);

        StructuredLogger.debug(log, "embedding_cache_stored")
            .field("query_hash", queryHash.substring(0, 8))
            .field("model", embeddingModel)
            .field("dimensions", embedding.length)
            .log();
    }

    /**
     * Cleanup old cache entries (run daily).
     */
    @Scheduled(cron = "0 0 3 * * *")  // 3 AM daily
    @Transactional
    public void cleanupOldEntries() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(TTL_DAYS);
        int deleted = cacheRepository.deleteOlderThan(threshold);

        if (deleted > 0) {
            StructuredLogger.info(log, "embedding_cache_cleanup")
                .field("deleted_entries", deleted)
                .field("ttl_days", TTL_DAYS)
                .log();
        }
    }

    /**
     * Get cache statistics for monitoring.
     */
    public CacheStats getStats() {
        long entries = cacheRepository.countEntries();
        long totalHits = cacheRepository.sumHitCount();
        return new CacheStats(entries, totalHits);
    }

    public record CacheStats(long entries, long totalHits) {}

    // --- Helper methods ---

    private record CacheHit(UUID id, String embeddingStr) {}

    /**
     * Normalize and hash query text.
     * Normalization: lowercase, trim, collapse whitespace.
     */
    private String hashQuery(String queryText) {
        String normalized = queryText.toLowerCase().trim().replaceAll("\\s+", " ");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Convert float array to pgvector string format: [0.1,0.2,0.3]
     */
    private String arrayToVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Parse pgvector string format back to float array.
     */
    private float[] parseVectorString(String vectorStr) {
        if (vectorStr == null || vectorStr.isEmpty()) {
            return new float[0];
        }
        // Remove brackets: [0.1,0.2,0.3] -> 0.1,0.2,0.3
        String inner = vectorStr.substring(1, vectorStr.length() - 1);
        String[] parts = inner.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
