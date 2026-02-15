package org.sirohi.smartnotebook.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sirohi.smartnotebook.dto.TaskStatusResponse;
import org.sirohi.smartnotebook.model.Document;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the ingestion_tasks table — enqueue tasks and check status.
 * Uses JdbcTemplate directly since ingestion_tasks is a queue, not a JPA
 * entity.
 */
@Service
public class IngestionService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IngestionService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public UUID enqueue(Document doc, Map<String, Object> configOverride) {
        UUID taskId = UUID.randomUUID();

        Map<String, Object> config = new java.util.HashMap<>();
        config.put("chunk_size", 512);
        config.put("chunk_overlap", 50);
        // Default embedding model will be overridden by worker config if not present
        // here
        // But if user provides one, it takes precedence.

        if (configOverride != null) {
            config.putAll(configOverride);
        }

        Map<String, Object> payload = Map.of(
                "document_id", doc.getId().toString(),
                "source_path", doc.getFilePath(),
                "content_type", doc.getContentType(),
                "config", config);

        jdbc.update("""
                INSERT INTO ingestion_tasks (id, document_id, payload)
                VALUES (?, ?, ?::jsonb)
                """,
                taskId,
                doc.getId(),
                toJson(payload));

        return taskId;
    }

    public Optional<TaskStatusResponse> getStatus(UUID taskId) {
        try {
            return Optional.of(jdbc.queryForObject("""
                    SELECT id, document_id, status, result, error_message,
                           retry_count, created_at, updated_at, completed_at
                    FROM ingestion_tasks WHERE id = ?
                    """,
                    (rs, rowNum) -> new TaskStatusResponse(
                            rs.getObject("id", UUID.class),
                            rs.getObject("document_id", UUID.class),
                            rs.getString("status"),
                            parseJson(rs.getString("result")),
                            rs.getString("error_message"),
                            rs.getInt("retry_count"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant(),
                            rs.getTimestamp("completed_at") != null
                                    ? rs.getTimestamp("completed_at").toInstant()
                                    : null),
                    taskId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null)
            return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }
}
