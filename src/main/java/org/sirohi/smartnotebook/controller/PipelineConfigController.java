package org.sirohi.smartnotebook.controller;

import jakarta.validation.Valid;
import org.sirohi.smartnotebook.dto.PipelineConfigRequest;
import org.sirohi.smartnotebook.dto.PipelineConfigResponse;
import org.sirohi.smartnotebook.service.PipelineConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for managing pipeline model configuration.
 *
 * Endpoints:
 * - GET /api/pipeline-config - Get global pipeline config
 * - PUT /api/pipeline-config - Update global config
 * - GET /api/notebooks/{id}/pipeline-config - Get notebook-specific config
 * - PUT /api/notebooks/{id}/pipeline-config - Update notebook config
 * - DELETE /api/notebooks/{id}/pipeline-config - Reset notebook config to global
 * - DELETE /api/notebooks/{id}/pipeline-config/{step} - Reset specific step to global
 */
@RestController
public class PipelineConfigController {

    private final PipelineConfigService configService;

    public PipelineConfigController(PipelineConfigService configService) {
        this.configService = configService;
    }

    // ─── Global Configuration ───

    /**
     * Get global pipeline configuration.
     */
    @GetMapping("/api/pipeline-config")
    public PipelineConfigResponse getGlobalConfig() {
        return configService.getGlobalConfig();
    }

    /**
     * Update global pipeline configuration.
     */
    @PutMapping("/api/pipeline-config")
    public PipelineConfigResponse updateGlobalConfig(@Valid @RequestBody PipelineConfigRequest request) {
        return configService.updateGlobalConfig(request);
    }

    // ─── Notebook-Specific Configuration ───

    /**
     * Get effective pipeline configuration for a notebook.
     * Falls back to global for unconfigured steps.
     */
    @GetMapping("/api/notebooks/{notebookId}/pipeline-config")
    public PipelineConfigResponse getNotebookConfig(@PathVariable UUID notebookId) {
        return configService.getNotebookConfig(notebookId);
    }

    /**
     * Update notebook-specific pipeline configuration.
     */
    @PutMapping("/api/notebooks/{notebookId}/pipeline-config")
    public PipelineConfigResponse updateNotebookConfig(
            @PathVariable UUID notebookId,
            @Valid @RequestBody PipelineConfigRequest request) {
        return configService.updateNotebookConfig(notebookId, request);
    }

    /**
     * Reset notebook configuration to use global defaults.
     */
    @DeleteMapping("/api/notebooks/{notebookId}/pipeline-config")
    public ResponseEntity<PipelineConfigResponse> resetNotebookConfig(@PathVariable UUID notebookId) {
        PipelineConfigResponse response = configService.resetNotebookConfig(notebookId);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset a specific step to use global default.
     */
    @DeleteMapping("/api/notebooks/{notebookId}/pipeline-config/{stepName}")
    public ResponseEntity<PipelineConfigResponse> resetNotebookStep(
            @PathVariable UUID notebookId,
            @PathVariable String stepName) {
        PipelineConfigResponse response = configService.resetNotebookStep(notebookId, stepName);
        return ResponseEntity.ok(response);
    }
}
