package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.LocalModelResponse;
import org.sirohi.smartnotebook.dto.ModelInstallResponse;
import org.sirohi.smartnotebook.exception.ResourceNotFoundException;
import org.sirohi.smartnotebook.model.LocalModel;
import org.sirohi.smartnotebook.repository.LocalModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import org.springframework.http.HttpMethod;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for managing local Ollama models.
 *
 * Features:
 * - List installed and available models
 * - Install/uninstall models via Ollama API
 * - Hardware-based recommendations
 * - Sync with Ollama to update installed status
 */
@Service
public class LocalModelService {

    private static final Logger log = LoggerFactory.getLogger(LocalModelService.class);

    private final LocalModelRepository modelRepository;
    private final HardwareDetectionService hardwareService;
    private final WebClient webClient;
    private final String ollamaBaseUrl;

    public LocalModelService(LocalModelRepository modelRepository,
                             HardwareDetectionService hardwareService,
                             @Value("${app.models.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.modelRepository = modelRepository;
        this.hardwareService = hardwareService;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.webClient = WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
    }

    /**
     * List all models (installed and available).
     */
    @Transactional(readOnly = true)
    public List<LocalModelResponse> listAllModels() {
        return modelRepository.findAll().stream()
                .map(LocalModelResponse::from)
                .toList();
    }

    /**
     * List only installed models.
     */
    @Transactional(readOnly = true)
    public List<LocalModelResponse> listInstalledModels() {
        return modelRepository.findByInstalledTrue().stream()
                .map(LocalModelResponse::from)
                .toList();
    }

    /**
     * List available models (not yet installed).
     */
    @Transactional(readOnly = true)
    public List<LocalModelResponse> listAvailableModels() {
        return modelRepository.findAll().stream()
                .filter(m -> !m.isInstalled())
                .map(LocalModelResponse::from)
                .toList();
    }

    /**
     * List recommended models based on hardware.
     */
    @Transactional(readOnly = true)
    public List<LocalModelResponse> listRecommendedModels() {
        // Ensure hardware is detected and recommendations updated
        hardwareService.getHardwareInfo();

        return modelRepository.findByRecommendedTrue().stream()
                .map(LocalModelResponse::from)
                .toList();
    }

    /**
     * Get a specific model by name.
     */
    @Transactional(readOnly = true)
    public LocalModelResponse getModel(String modelName) {
        LocalModel model = modelRepository.findByModelName(modelName)
                .orElseThrow(() -> new ResourceNotFoundException("Model not found: " + modelName));
        return LocalModelResponse.from(model);
    }

    /**
     * Install a model (async via Ollama pull).
     */
    @Transactional
    public ModelInstallResponse installModel(String modelName) {
        log.info("Installing model: {}", modelName);

        LocalModel model = modelRepository.findByModelName(modelName)
                .orElseThrow(() -> new ResourceNotFoundException("Model not found in curated list: " + modelName));

        if (model.isInstalled()) {
            return ModelInstallResponse.alreadyInstalled(modelName);
        }

        // Check hardware compatibility
        if (!hardwareService.canRunModel(model.getMinRamGb())) {
            log.warn("Model {} requires {}GB RAM but system has insufficient resources",
                    modelName, model.getMinRamGb());
            return ModelInstallResponse.failed(modelName,
                    "Insufficient RAM. Model requires " + model.getMinRamGb() + "GB");
        }

        // Update status to installing
        modelRepository.updateInstallStatus(modelName, "installing", 0);

        // Start async installation
        startModelPull(modelName);

        return ModelInstallResponse.started(modelName);
    }

    /**
     * Uninstall a model.
     */
    @Transactional
    public void uninstallModel(String modelName) {
        log.info("Uninstalling model: {}", modelName);

        LocalModel model = modelRepository.findByModelName(modelName)
                .orElseThrow(() -> new ResourceNotFoundException("Model not found: " + modelName));

        if (!model.isInstalled()) {
            log.debug("Model {} is not installed", modelName);
            return;
        }

        try {
            // Call Ollama delete API (using method() since delete() doesn't support body)
            webClient.method(HttpMethod.DELETE)
                    .uri("/api/delete")
                    .bodyValue(Map.of("name", modelName))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            // Update database
            modelRepository.markUninstalled(modelName);

            log.info("Model uninstalled: {}", modelName);

        } catch (Exception e) {
            log.error("Failed to uninstall model: {}", modelName, e);
            throw new RuntimeException("Failed to uninstall model: " + e.getMessage());
        }
    }

    /**
     * Sync with Ollama to update installed status for all models.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public void syncWithOllama() {
        log.info("Syncing with Ollama...");

        try {
            // Get list of installed models from Ollama
            Map<String, Object> response = webClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("models")) {
                log.warn("No models returned from Ollama");
                return;
            }

            List<Map<String, Object>> ollamaModels = (List<Map<String, Object>>) response.get("models");
            List<String> installedNames = ollamaModels.stream()
                    .map(m -> (String) m.get("name"))
                    .map(name -> name.contains(":") ? name : name + ":latest")
                    .toList();

            log.debug("Ollama has {} models installed", installedNames.size());

            // Update all models in database
            for (LocalModel model : modelRepository.findAll()) {
                String modelName = model.getModelName();
                boolean isInstalled = installedNames.stream()
                        .anyMatch(name -> name.startsWith(modelName) || modelName.startsWith(name.split(":")[0]));

                if (isInstalled && !model.isInstalled()) {
                    // Model was installed externally
                    modelRepository.markInstalled(modelName, OffsetDateTime.now());
                    log.debug("Model {} marked as installed", modelName);

                    // Try to get size
                    ollamaModels.stream()
                            .filter(m -> ((String) m.get("name")).startsWith(modelName))
                            .findFirst()
                            .ifPresent(m -> {
                                Object size = m.get("size");
                                if (size instanceof Number) {
                                    modelRepository.updateSize(modelName, ((Number) size).longValue());
                                }
                            });

                } else if (!isInstalled && model.isInstalled()) {
                    // Model was uninstalled externally
                    modelRepository.markUninstalled(modelName);
                    log.debug("Model {} marked as uninstalled", modelName);
                }
            }

            log.info("Sync complete. {} models found in Ollama.", installedNames.size());

        } catch (Exception e) {
            log.error("Failed to sync with Ollama", e);
        }
    }

    /**
     * Get models by type (llm or embedding).
     */
    @Transactional(readOnly = true)
    public List<LocalModelResponse> getModelsByType(String modelType) {
        return modelRepository.findByModelType(modelType).stream()
                .map(LocalModelResponse::from)
                .toList();
    }

    /**
     * Get installed models by type.
     */
    @Transactional(readOnly = true)
    public List<LocalModelResponse> getInstalledModelsByType(String modelType) {
        return modelRepository.findByModelTypeAndInstalledTrue(modelType).stream()
                .map(LocalModelResponse::from)
                .toList();
    }

    // ─── Private Helpers ───

    private void startModelPull(String modelName) {
        // Run pull in background thread
        Thread.startVirtualThread(() -> {
            try {
                log.info("Starting Ollama pull for: {}", modelName);

                // Use streaming pull to track progress
                Flux<Map> pullStream = webClient.post()
                        .uri("/api/pull")
                        .bodyValue(Map.of("name", modelName, "stream", true))
                        .retrieve()
                        .bodyToFlux(Map.class);

                pullStream.subscribe(
                        event -> {
                            // Parse progress from Ollama events
                            Object status = event.get("status");
                            Object completed = event.get("completed");
                            Object total = event.get("total");

                            if (completed != null && total != null) {
                                long comp = ((Number) completed).longValue();
                                long tot = ((Number) total).longValue();
                                int progress = tot > 0 ? (int) ((comp * 100) / tot) : 0;
                                modelRepository.updateInstallStatus(modelName, "installing", progress);
                            }

                            log.debug("Pull progress for {}: {}", modelName, status);
                        },
                        error -> {
                            log.error("Pull failed for {}: {}", modelName, error.getMessage());
                            modelRepository.updateInstallStatus(modelName, "failed", 0);
                        },
                        () -> {
                            log.info("Pull completed for: {}", modelName);
                            modelRepository.markInstalled(modelName, OffsetDateTime.now());
                        }
                );

            } catch (Exception e) {
                log.error("Failed to start pull for {}: {}", modelName, e.getMessage());
                modelRepository.updateInstallStatus(modelName, "failed", 0);
            }
        });
    }
}
