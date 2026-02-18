package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.PipelineConfigRequest;
import org.sirohi.smartnotebook.dto.PipelineConfigResponse;
import org.sirohi.smartnotebook.dto.PipelineConfigResponse.StepConfig;
import org.sirohi.smartnotebook.exception.ResourceNotFoundException;
import org.sirohi.smartnotebook.model.PipelineModelConfig;
import org.sirohi.smartnotebook.repository.NotebookRepository;
import org.sirohi.smartnotebook.repository.PipelineModelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing pipeline model configuration.
 * Handles both global defaults and notebook-specific overrides.
 */
@Service
public class PipelineConfigService {

    private static final Logger log = LoggerFactory.getLogger(PipelineConfigService.class);

    private static final String STEP_EXTRACTION = "extraction";
    private static final String STEP_EMBEDDING = "embedding";
    private static final String STEP_CHAT = "chat";

    private final PipelineModelConfigRepository configRepository;
    private final NotebookRepository notebookRepository;
    private final ProviderCredentialService credentialService;

    public PipelineConfigService(PipelineModelConfigRepository configRepository,
                                NotebookRepository notebookRepository,
                                ProviderCredentialService credentialService) {
        this.configRepository = configRepository;
        this.notebookRepository = notebookRepository;
        this.credentialService = credentialService;
    }

    /**
     * Get global pipeline configuration.
     */
    @Transactional(readOnly = true)
    public PipelineConfigResponse getGlobalConfig() {
        List<PipelineModelConfig> configs = configRepository.findAllGlobalConfigs();
        return buildResponse(null, configs);
    }

    /**
     * Get effective pipeline configuration for a notebook.
     * Falls back to global config for steps without notebook-specific config.
     */
    @Transactional(readOnly = true)
    public PipelineConfigResponse getNotebookConfig(UUID notebookId) {
        if (!notebookRepository.existsById(notebookId)) {
            throw new ResourceNotFoundException("Notebook not found: " + notebookId);
        }

        // Get notebook-specific configs
        List<PipelineModelConfig> notebookConfigs = configRepository.findByNotebookIdAndActiveTrue(notebookId);

        // Build response, falling back to global for missing steps
        return buildResponseWithFallback(notebookId, notebookConfigs);
    }

    /**
     * Update global pipeline configuration.
     */
    @Transactional
    public PipelineConfigResponse updateGlobalConfig(PipelineConfigRequest request) {
        log.info("Updating global pipeline configuration");

        if (request.extraction() != null) {
            updateGlobalStep(STEP_EXTRACTION, request.extraction());
        }
        if (request.embedding() != null) {
            updateGlobalStep(STEP_EMBEDDING, request.embedding());
        }
        if (request.chat() != null) {
            updateGlobalStep(STEP_CHAT, request.chat());
        }

        return getGlobalConfig();
    }

    /**
     * Update notebook-specific pipeline configuration.
     */
    @Transactional
    public PipelineConfigResponse updateNotebookConfig(UUID notebookId, PipelineConfigRequest request) {
        if (!notebookRepository.existsById(notebookId)) {
            throw new ResourceNotFoundException("Notebook not found: " + notebookId);
        }

        log.info("Updating pipeline configuration for notebook: {}", notebookId);

        if (request.extraction() != null) {
            updateNotebookStep(notebookId, STEP_EXTRACTION, request.extraction());
        }
        if (request.embedding() != null) {
            updateNotebookStep(notebookId, STEP_EMBEDDING, request.embedding());
        }
        if (request.chat() != null) {
            updateNotebookStep(notebookId, STEP_CHAT, request.chat());
        }

        return getNotebookConfig(notebookId);
    }

    /**
     * Reset notebook configuration to use global defaults.
     */
    @Transactional
    public PipelineConfigResponse resetNotebookConfig(UUID notebookId) {
        if (!notebookRepository.existsById(notebookId)) {
            throw new ResourceNotFoundException("Notebook not found: " + notebookId);
        }

        log.info("Resetting pipeline configuration for notebook: {}", notebookId);
        configRepository.deleteByNotebookId(notebookId);

        return getGlobalConfig();
    }

    /**
     * Reset a specific step to use global default.
     */
    @Transactional
    public PipelineConfigResponse resetNotebookStep(UUID notebookId, String stepName) {
        if (!notebookRepository.existsById(notebookId)) {
            throw new ResourceNotFoundException("Notebook not found: " + notebookId);
        }

        log.info("Resetting {} configuration for notebook: {}", stepName, notebookId);
        configRepository.deleteByNotebookIdAndStepName(notebookId, stepName);

        return getNotebookConfig(notebookId);
    }

    /**
     * Get effective config for a specific step (for use by other services).
     */
    @Transactional(readOnly = true)
    public PipelineModelConfig getEffectiveConfigForStep(UUID notebookId, String stepName) {
        List<PipelineModelConfig> configs = configRepository.findEffectiveConfig(notebookId, stepName);
        if (configs.isEmpty()) {
            // Try global fallback
            return configRepository.findGlobalConfigForStep(stepName)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No configuration found for step: " + stepName));
        }
        return configs.get(0);
    }

    /**
     * Get all configs using a specific provider (for validation before disabling).
     */
    @Transactional(readOnly = true)
    public List<PipelineModelConfig> getConfigsUsingProvider(String providerName) {
        return configRepository.findByProviderNameAndActiveTrue(providerName);
    }

    // ─── Private Helpers ───

    private void updateGlobalStep(String stepName, PipelineConfigRequest.StepConfigRequest stepConfig) {
        PipelineModelConfig config = configRepository.findGlobalConfigForStep(stepName)
                .orElseGet(() -> {
                    PipelineModelConfig newConfig = new PipelineModelConfig();
                    newConfig.setStepName(stepName);
                    return newConfig;
                });

        config.setProviderName(stepConfig.providerName());
        config.setModelName(stepConfig.modelName());
        config.setParameters(stepConfig.parametersOrDefault());
        config.setActive(true);

        configRepository.save(config);
        log.debug("Updated global {} config: {} / {}", stepName, stepConfig.providerName(), stepConfig.modelName());
    }

    private void updateNotebookStep(UUID notebookId, String stepName, PipelineConfigRequest.StepConfigRequest stepConfig) {
        PipelineModelConfig config = configRepository.findByNotebookIdAndStepName(notebookId, stepName)
                .orElseGet(() -> {
                    PipelineModelConfig newConfig = new PipelineModelConfig();
                    newConfig.setNotebookId(notebookId);
                    newConfig.setStepName(stepName);
                    return newConfig;
                });

        config.setProviderName(stepConfig.providerName());
        config.setModelName(stepConfig.modelName());
        config.setParameters(stepConfig.parametersOrDefault());
        config.setActive(true);

        configRepository.save(config);
        log.debug("Updated notebook {} {} config: {} / {}", notebookId, stepName, stepConfig.providerName(), stepConfig.modelName());
    }

    private PipelineConfigResponse buildResponse(UUID notebookId, List<PipelineModelConfig> configs) {
        Map<String, PipelineModelConfig> byStep = configs.stream()
                .collect(java.util.stream.Collectors.toMap(
                        PipelineModelConfig::getStepName,
                        c -> c,
                        (a, b) -> a  // Keep first (shouldn't happen with unique constraint)
                ));

        StepConfig extraction = getStepConfig(byStep.get(STEP_EXTRACTION));
        StepConfig embedding = getStepConfig(byStep.get(STEP_EMBEDDING));
        StepConfig chat = getStepConfig(byStep.get(STEP_CHAT));

        OffsetDateTime updatedAt = configs.stream()
                .map(PipelineModelConfig::getUpdatedAt)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        return new PipelineConfigResponse(
                notebookId,
                notebookId == null,
                extraction,
                embedding,
                chat,
                updatedAt
        );
    }

    private PipelineConfigResponse buildResponseWithFallback(UUID notebookId, List<PipelineModelConfig> notebookConfigs) {
        Map<String, PipelineModelConfig> byStep = notebookConfigs.stream()
                .collect(java.util.stream.Collectors.toMap(
                        PipelineModelConfig::getStepName,
                        c -> c,
                        (a, b) -> a
                ));

        // Fall back to global for missing steps
        StepConfig extraction = getStepConfigWithFallback(byStep.get(STEP_EXTRACTION), STEP_EXTRACTION);
        StepConfig embedding = getStepConfigWithFallback(byStep.get(STEP_EMBEDDING), STEP_EMBEDDING);
        StepConfig chat = getStepConfigWithFallback(byStep.get(STEP_CHAT), STEP_CHAT);

        OffsetDateTime updatedAt = notebookConfigs.stream()
                .map(PipelineModelConfig::getUpdatedAt)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        return new PipelineConfigResponse(
                notebookId,
                false,
                extraction,
                embedding,
                chat,
                updatedAt
        );
    }

    private StepConfig getStepConfig(PipelineModelConfig config) {
        if (config == null) {
            return StepConfig.empty();
        }
        boolean available = credentialService.isProviderAvailable(config.getProviderName());
        return StepConfig.from(config, available);
    }

    private StepConfig getStepConfigWithFallback(PipelineModelConfig config, String stepName) {
        if (config != null) {
            return getStepConfig(config);
        }
        // Fall back to global
        return configRepository.findGlobalConfigForStep(stepName)
                .map(this::getStepConfig)
                .orElse(StepConfig.empty());
    }
}
