package org.sirohi.smartnotebook.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sirohi.smartnotebook.dto.TaskStatusResponse;
import org.sirohi.smartnotebook.logging.StructuredLogger;
import org.sirohi.smartnotebook.model.Document;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IngestionService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IngestionService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public UUID enqueue(Document doc, Map<String, Object> configOverride) {
        return enqueueInternal(doc, configOverride, "FULL_INGESTION", 0, null, false);
    }

    public UUID enqueueReprocessing(Document doc, Map<String, Object> configOverride, String reason) {
        return enqueueInternal(doc, configOverride, "REPROCESSING", 1, reason, true);
    }

    private UUID enqueueInternal(Document doc,
                                 Map<String, Object> configOverride,
                                 String taskType,
                                 int priority,
                                 String reason,
                                 boolean dedupeQueuedTasks) {
        UUID taskId = UUID.randomUUID();

        Map<String, Object> config = new HashMap<>();
        config.put("chunk_size", 512);
        config.put("chunk_overlap", 50);
        // Default embedding model will be overridden by worker config if not present
        // here
        // But if user provides one, it takes precedence.

        if (configOverride != null) {
            config.putAll(configOverride);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("document_id", doc.getId().toString());
        payload.put("source_path", doc.getFilePath());
        payload.put("content_type", doc.getContentType());
        payload.put("config", config);
        if (reason != null && !reason.isBlank()) {
            payload.put("reprocess_reason", reason);
        }

        int dedupedTasks = 0;
        if (dedupeQueuedTasks) {
            dedupedTasks = jdbc.update("""
                    DELETE FROM ingestion_tasks
                    WHERE document_id = ?
                      AND status IN ('PENDING', 'FAILED')
                    """, doc.getId());
        }

        jdbc.update("""
                INSERT INTO ingestion_tasks (id, document_id, task_type, priority, payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """,
                taskId,
                doc.getId(),
                taskType,
                priority,
                toJson(payload));

        StructuredLogger.info(log, "ingestion_task_enqueued")
                .field("task_id", taskId)
                .field("document_id", doc.getId())
                .field("task_type", taskType)
                .field("priority", priority)
                .field("reprocess_reason", reason)
                .field("deduped_queued_tasks", dedupedTasks)
                .field("config", config)
                .log();

        return taskId;
    }

    public Optional<TaskStatusResponse> getStatus(UUID taskId) {
        try {
            TaskStatusResponse response = jdbc.queryForObject("""
                    SELECT id, document_id, status, result, error_message,
                           error_details, retry_count, created_at, updated_at, completed_at
                    FROM ingestion_tasks WHERE id = ?
                    """,
                    (rs, rowNum) -> new TaskStatusResponse(
                            rs.getObject("id", UUID.class),
                            rs.getObject("document_id", UUID.class),
                            rs.getString("status"),
                            parseJson(rs.getString("result")),
                            rs.getString("error_message"),
                            parseJson(rs.getString("error_details")),
                            rs.getInt("retry_count"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant(),
                            rs.getTimestamp("completed_at") != null
                                    ? rs.getTimestamp("completed_at").toInstant()
                                    : null),
                    taskId);

            if ("FAILED".equalsIgnoreCase(response.status()) || "DEAD_LETTER".equalsIgnoreCase(response.status())) {
                StructuredLogger.warn(log, "ingestion_task_failed_status")
                        .field("task_id", response.taskId())
                        .field("document_id", response.documentId())
                        .field("status", response.status())
                        .field("retry_count", response.retryCount())
                        .field("error_message", response.errorMessage())
                        .field("error_details", response.errorDetails())
                        .log();
            }

            return Optional.of(response);
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
