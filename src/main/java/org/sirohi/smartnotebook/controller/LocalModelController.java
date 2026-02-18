package org.sirohi.smartnotebook.controller;

import org.sirohi.smartnotebook.dto.HardwareInfoResponse;
import org.sirohi.smartnotebook.dto.LocalModelResponse;
import org.sirohi.smartnotebook.dto.ModelInstallResponse;
import org.sirohi.smartnotebook.service.HardwareDetectionService;
import org.sirohi.smartnotebook.service.LocalModelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing local Ollama models.
 *
 * Endpoints:
 * - GET /api/models/local - List all models
 * - GET /api/models/local/installed - List installed models
 * - GET /api/models/local/available - List available (not installed) models
 * - GET /api/models/local/recommendations - Get recommended models for this hardware
 * - GET /api/models/local/{modelName} - Get specific model details
 * - POST /api/models/local/{modelName}/install - Install a model
 * - DELETE /api/models/local/{modelName} - Uninstall a model
 * - POST /api/models/local/sync - Sync with Ollama
 * - GET /api/models/local/hardware - Get hardware info
 */
@RestController
@RequestMapping("/api/models/local")
public class LocalModelController {

    private final LocalModelService modelService;
    private final HardwareDetectionService hardwareService;

    public LocalModelController(LocalModelService modelService,
                               HardwareDetectionService hardwareService) {
        this.modelService = modelService;
        this.hardwareService = hardwareService;
    }

    /**
     * List all models (installed and available).
     */
    @GetMapping
    public List<LocalModelResponse> listAllModels() {
        return modelService.listAllModels();
    }

    /**
     * List installed models only.
     */
    @GetMapping("/installed")
    public List<LocalModelResponse> listInstalledModels() {
        return modelService.listInstalledModels();
    }

    /**
     * List available models (not yet installed).
     */
    @GetMapping("/available")
    public List<LocalModelResponse> listAvailableModels() {
        return modelService.listAvailableModels();
    }

    /**
     * Get recommended models based on hardware.
     */
    @GetMapping("/recommendations")
    public List<LocalModelResponse> getRecommendedModels() {
        return modelService.listRecommendedModels();
    }

    /**
     * Get models by type (llm or embedding).
     */
    @GetMapping("/type/{modelType}")
    public List<LocalModelResponse> getModelsByType(@PathVariable String modelType) {
        return modelService.getModelsByType(modelType);
    }

    /**
     * Get a specific model's details.
     */
    @GetMapping("/{modelName}")
    public LocalModelResponse getModel(@PathVariable String modelName) {
        return modelService.getModel(modelName);
    }

    /**
     * Install a model from the curated list.
     */
    @PostMapping("/{modelName}/install")
    public ResponseEntity<ModelInstallResponse> installModel(@PathVariable String modelName) {
        ModelInstallResponse response = modelService.installModel(modelName);

        return switch (response.status()) {
            case "started" -> ResponseEntity.accepted().body(response);
            case "already_installed" -> ResponseEntity.ok(response);
            case "failed" -> ResponseEntity.badRequest().body(response);
            default -> ResponseEntity.ok(response);
        };
    }

    /**
     * Uninstall a model.
     */
    @DeleteMapping("/{modelName}")
    public ResponseEntity<Void> uninstallModel(@PathVariable String modelName) {
        modelService.uninstallModel(modelName);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sync with Ollama to update installed status.
     */
    @PostMapping("/sync")
    public ResponseEntity<Void> syncWithOllama() {
        modelService.syncWithOllama();
        return ResponseEntity.ok().build();
    }

    /**
     * Get hardware information.
     */
    @GetMapping("/hardware")
    public HardwareInfoResponse getHardwareInfo() {
        return hardwareService.getHardwareInfoResponse();
    }

    /**
     * Refresh hardware detection.
     */
    @PostMapping("/hardware/refresh")
    public HardwareInfoResponse refreshHardware() {
        hardwareService.refresh();
        return hardwareService.getHardwareInfoResponse();
    }
}
