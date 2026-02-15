package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.dto.ChunkMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Lightweight query audit log repository.
 * Fire-and-forget logging — failures here should never affect query responses.
 */
@Repository
public class QueryLogRepository {

    private static final Logger log = LoggerFactory.getLogger(QueryLogRepository.class);

    private final JdbcTemplate jdbc;

    public QueryLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void logAsync(String question, String answer, List<ChunkMatch> matches, long latencyMs) {
        try {
            UUID[] chunkIds = matches.stream()
                    .map(ChunkMatch::chunkId)
                    .toArray(UUID[]::new);

            jdbc.update("""
                    INSERT INTO query_log (question, answer, chunks_used, model_used, latency_ms)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    question,
                    answer,
                    chunkIds,
                    "phi3:mini",
                    (int) latencyMs);
        } catch (Exception e) {
            log.warn("Failed to log query (non-critical): {}", e.getMessage());
        }
    }
}
