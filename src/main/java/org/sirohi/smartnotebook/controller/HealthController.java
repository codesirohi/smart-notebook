package org.sirohi.smartnotebook.controller;

import org.sirohi.smartnotebook.gateway.ModelHealth;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final org.sirohi.smartnotebook.gateway.GatewayFactory gatewayFactory;
    private final JdbcTemplate jdbc;

    public HealthController(org.sirohi.smartnotebook.gateway.GatewayFactory gatewayFactory, JdbcTemplate jdbc) {
        this.gatewayFactory = gatewayFactory;
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now());

        // Database health
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            health.put("database", Map.of("status", "UP"));
        } catch (Exception e) {
            health.put("database", Map.of("status", "DOWN", "error", e.getMessage()));
            health.put("status", "DEGRADED");
        }

        // Models health
        Map<String, Object> modelsStatus = new LinkedHashMap<>();
        boolean anyUp = false;
        for (var gw : gatewayFactory.getAllGateways()) {
            ModelHealth h = gw.health();
            modelsStatus.put(h.provider(), Map.of(
                    "status", h.available() ? "UP" : "DOWN",
                    "model", h.model(),
                    "message", h.message()));
            if (h.available())
                anyUp = true;
        }
        health.put("models", modelsStatus);

        if (!anyUp && !modelsStatus.isEmpty()) {
            health.put("status", "DEGRADED");
        }

        // Queue stats
        try {
            Map<String, Object> queueStats = new LinkedHashMap<>();
            int pending = jdbc.queryForObject(
                    "SELECT count(*) FROM ingestion_tasks WHERE status = 'PENDING'", Integer.class);
            int processing = jdbc.queryForObject(
                    "SELECT count(*) FROM ingestion_tasks WHERE status = 'PROCESSING'", Integer.class);
            int failed = jdbc.queryForObject(
                    "SELECT count(*) FROM ingestion_tasks WHERE status = 'FAILED'", Integer.class);
            int deadLetter = jdbc.queryForObject(
                    "SELECT count(*) FROM ingestion_tasks WHERE status = 'DEAD_LETTER'", Integer.class);

            queueStats.put("pending", pending);
            queueStats.put("processing", processing);
            queueStats.put("failed", failed);
            queueStats.put("dead_letter", deadLetter);
            health.put("queue", queueStats);
        } catch (Exception e) {
            health.put("queue", Map.of("error", e.getMessage()));
        }

        // Worker heartbeat readiness
        try {
            Timestamp lastSeenTs = jdbc.queryForObject(
                    "SELECT max(last_seen_at) FROM worker_heartbeats",
                    Timestamp.class);

            Map<String, Object> worker = new LinkedHashMap<>();
            if (lastSeenTs == null) {
                worker.put("status", "DOWN");
                worker.put("message", "No worker heartbeat received yet");
                health.put("status", "DEGRADED");
            } else {
                Instant lastSeen = lastSeenTs.toInstant();
                long staleSeconds = java.time.Duration.between(lastSeen, Instant.now()).getSeconds();
                boolean isUp = staleSeconds <= 15;

                worker.put("status", isUp ? "UP" : "DOWN");
                worker.put("lastSeenAt", lastSeen);
                worker.put("staleSeconds", staleSeconds);
                if (!isUp) {
                    worker.put("message", "Worker heartbeat stale");
                    health.put("status", "DEGRADED");
                }
            }
            health.put("worker", worker);
        } catch (Exception e) {
            health.put("worker", Map.of(
                    "status", "UNKNOWN",
                    "message", e.getMessage()));
            health.put("status", "DEGRADED");
        }

        HttpStatus status = "UP".equals(health.get("status"))
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(health);
    }
}
