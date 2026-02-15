package org.sirohi.smartnotebook.controller;

import org.sirohi.smartnotebook.gateway.ModelGateway;
import org.sirohi.smartnotebook.gateway.ModelHealth;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
            queueStats.put("pending", jdbc.queryForObject(
                    "SELECT count(*) FROM ingestion_tasks WHERE status = 'PENDING'", Integer.class));
            queueStats.put("processing", jdbc.queryForObject(
                    "SELECT count(*) FROM ingestion_tasks WHERE status = 'PROCESSING'", Integer.class));
            queueStats.put("failed", jdbc.queryForObject(
                    "SELECT count(*) FROM ingestion_tasks WHERE status = 'FAILED'", Integer.class));
            queueStats.put("dead_letter", jdbc.queryForObject(
                    "SELECT count(*) FROM ingestion_tasks WHERE status = 'DEAD_LETTER'", Integer.class));
            health.put("queue", queueStats);
        } catch (Exception e) {
            health.put("queue", Map.of("error", e.getMessage()));
        }

        HttpStatus status = "UP".equals(health.get("status"))
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(health);
    }
}
