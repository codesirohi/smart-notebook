package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.PipelineConfigRequest;
import org.sirohi.smartnotebook.dto.PipelineConfigResponse;
import org.sirohi.smartnotebook.dto.PipelineConfigResponse.StepConfig;
import org.sirohi.smartnotebook.dto.ProviderStatusResponse;
import org.sirohi.smartnotebook.exception.BadRequestException;
import org.sirohi.smartnotebook.exception.ResourceNotFoundException;
import org.sirohi.smartnotebook.model.Document;
import org.sirohi.smartnotebook.model.PipelineModelConfig;
import org.sirohi.smartnotebook.repository.DocumentRepository;
import org.sirohi.smartnotebook.repository.NotebookRepository;
import org.sirohi.smartnotebook.repository.PipelineModelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;
    private final ProviderCredentialService credentialService;

    public PipelineConfigService(PipelineModelConfigRepository configRepository,
                                NotebookRepository notebookRepository,
                                DocumentRepository documentRepository,
                                IngestionService ingestionService,
                                ProviderCredentialService credentialService) {
        this.configRepository = configRepository;
        this.notebookRepository = notebookRepository;
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
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

        StepSignature oldGlobalExtraction = getGlobalStepSignature(STEP_EXTRACTION);
        StepSignature oldGlobalEmbedding = getGlobalStepSignature(STEP_EMBEDDING);

        if (request.extraction() != null) {
            updateGlobalStep(STEP_EXTRACTION, request.extraction());
        }
        if (request.embedding() != null) {
            updateGlobalStep(STEP_EMBEDDING, request.embedding());
        }
        if (request.chat() != null) {
            updateGlobalStep(STEP_CHAT, request.chat());
        }

        StepSignature newGlobalExtraction = getGlobalStepSignature(STEP_EXTRACTION);
        StepSignature newGlobalEmbedding = getGlobalStepSignature(STEP_EMBEDDING);
        boolean extractionChanged = !Objects.equals(oldGlobalExtraction, newGlobalExtraction);
        boolean embeddingChanged = !Objects.equals(oldGlobalEmbedding, newGlobalEmbedding);

        if (extractionChanged || embeddingChanged) {
            int queued = enqueueGlobalReprocessing(extractionChanged, embeddingChanged);
            log.info("Queued {} document reprocessing task(s) due to global pipeline config change", queued);
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
        StepSignature oldExtraction = getEffectiveStepSignature(notebookId, STEP_EXTRACTION);
        StepSignature oldEmbedding = getEffectiveStepSignature(notebookId, STEP_EMBEDDING);

        if (request.extraction() != null) {
            updateNotebookStep(notebookId, STEP_EXTRACTION, request.extraction());
        }
        if (request.embedding() != null) {
            updateNotebookStep(notebookId, STEP_EMBEDDING, request.embedding());
        }
        if (request.chat() != null) {
            updateNotebookStep(notebookId, STEP_CHAT, request.chat());
        }

        StepSignature newExtraction = getEffectiveStepSignature(notebookId, STEP_EXTRACTION);
        StepSignature newEmbedding = getEffectiveStepSignature(notebookId, STEP_EMBEDDING);
        if (!Objects.equals(oldExtraction, newExtraction) || !Objects.equals(oldEmbedding, newEmbedding)) {
            int queued = enqueueNotebookReprocessing(notebookId, "notebook_pipeline_config_updated");
            log.info("Queued {} document reprocessing task(s) for notebook {}", queued, notebookId);
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

        StepSignature oldExtraction = getEffectiveStepSignature(notebookId, STEP_EXTRACTION);
        StepSignature oldEmbedding = getEffectiveStepSignature(notebookId, STEP_EMBEDDING);

        log.info("Resetting pipeline configuration for notebook: {}", notebookId);
        configRepository.deleteByNotebookId(notebookId);

        StepSignature newExtraction = getEffectiveStepSignature(notebookId, STEP_EXTRACTION);
        StepSignature newEmbedding = getEffectiveStepSignature(notebookId, STEP_EMBEDDING);
        if (!Objects.equals(oldExtraction, newExtraction) || !Objects.equals(oldEmbedding, newEmbedding)) {
            int queued = enqueueNotebookReprocessing(notebookId, "notebook_pipeline_config_reset");
            log.info("Queued {} document reprocessing task(s) for notebook {}", queued, notebookId);
        }

        return getNotebookConfig(notebookId);
    }

    /**
     * Reset a specific step to use global default.
     */
    @Transactional
    public PipelineConfigResponse resetNotebookStep(UUID notebookId, String stepName) {
        if (!notebookRepository.existsById(notebookId)) {
            throw new ResourceNotFoundException("Notebook not found: " + notebookId);
        }

        String normalizedStepName = normalizeStepName(stepName);
        StepSignature oldStepSignature = getEffectiveStepSignature(notebookId, normalizedStepName);
        log.info("Resetting {} configuration for notebook: {}", normalizedStepName, notebookId);
        configRepository.deleteByNotebookIdAndStepName(notebookId, normalizedStepName);

        StepSignature newStepSignature = getEffectiveStepSignature(notebookId, normalizedStepName);
        if (isReprocessingStep(normalizedStepName) && !Objects.equals(oldStepSignature, newStepSignature)) {
            int queued = enqueueNotebookReprocessing(notebookId, "notebook_pipeline_step_reset:" + normalizedStepName);
            log.info("Queued {} document reprocessing task(s) for notebook {} after {} reset", queued, notebookId, normalizedStepName);
        }

        return getNotebookConfig(notebookId);
    }

    /**
     * Get effective config for a specific step (for use by other services).
     */
    @Transactional(readOnly = true)
    public PipelineModelConfig getEffectiveConfigForStep(UUID notebookId, String stepName) {
        String normalizedStepName = normalizeStepName(stepName);
        List<PipelineModelConfig> configs = configRepository.findEffectiveConfig(notebookId, normalizedStepName);
        if (configs.isEmpty()) {
            // Try global fallback
            return configRepository.findGlobalConfigForStep(normalizedStepName)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No configuration found for step: " + normalizedStepName));
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
        String normalizedStepName = normalizeStepName(stepName);
        validateStepConfig(normalizedStepName, stepConfig);

        PipelineModelConfig config = configRepository.findGlobalConfigForStep(normalizedStepName)
                .orElseGet(() -> {
                    PipelineModelConfig newConfig = new PipelineModelConfig();
                    newConfig.setStepName(normalizedStepName);
                    return newConfig;
                });

        config.setProviderName(stepConfig.providerName().trim().toLowerCase());
        config.setModelName(stepConfig.modelName());
        config.setParameters(stepConfig.parametersOrDefault());
        config.setActive(true);

        configRepository.save(config);
        log.debug("Updated global {} config: {} / {}", normalizedStepName, stepConfig.providerName(), stepConfig.modelName());
    }

    private void updateNotebookStep(UUID notebookId, String stepName, PipelineConfigRequest.StepConfigRequest stepConfig) {
        String normalizedStepName = normalizeStepName(stepName);
        validateStepConfig(normalizedStepName, stepConfig);

        PipelineModelConfig config = configRepository.findByNotebookIdAndStepName(notebookId, normalizedStepName)
                .orElseGet(() -> {
                    PipelineModelConfig newConfig = new PipelineModelConfig();
                    newConfig.setNotebookId(notebookId);
                    newConfig.setStepName(normalizedStepName);
                    return newConfig;
                });

        config.setProviderName(stepConfig.providerName().trim().toLowerCase());
        config.setModelName(stepConfig.modelName());
        config.setParameters(stepConfig.parametersOrDefault());
        config.setActive(true);

        configRepository.save(config);
        log.debug("Updated notebook {} {} config: {} / {}", notebookId, normalizedStepName, stepConfig.providerName(), stepConfig.modelName());
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

    private boolean isReprocessingStep(String stepName) {
        return STEP_EXTRACTION.equals(stepName) || STEP_EMBEDDING.equals(stepName);
    }

    private void validateStepConfig(String stepName, PipelineConfigRequest.StepConfigRequest stepConfig) {
        String providerName = stepConfig.providerName().trim().toLowerCase();
        if (!credentialService.isProviderAvailable(providerName)) {
            throw new BadRequestException(
                    "Provider '" + providerName + "' is disabled or missing credentials. Enable/configure it before assigning this stage.");
        }

        String requiredOperation = requiredOperationForStep(stepName);
        List<String> supportedOperations = ProviderStatusResponse.getSupportedOperations(providerName);

        if (!supportedOperations.contains(requiredOperation)) {
            throw new BadRequestException(
                    "Provider '" + providerName + "' does not support '" + requiredOperation
                            + "' for pipeline step '" + stepName + "'.");
        }
    }

    private String requiredOperationForStep(String stepName) {
        return switch (stepName) {
            case STEP_EXTRACTION, STEP_CHAT -> "completion";
            case STEP_EMBEDDING -> "embedding";
            default -> throw new BadRequestException("Unsupported pipeline step: " + stepName);
        };
    }

    private String normalizeStepName(String stepName) {
        if (stepName == null || stepName.isBlank()) {
            throw new BadRequestException("Pipeline step name is required");
        }
        String normalized = stepName.trim().toLowerCase();
        if (!STEP_EXTRACTION.equals(normalized) && !STEP_EMBEDDING.equals(normalized) && !STEP_CHAT.equals(normalized)) {
            throw new BadRequestException("Unsupported pipeline step: " + stepName);
        }
        return normalized;
    }

    private StepSignature getGlobalStepSignature(String stepName) {
        return configRepository.findGlobalConfigForStep(stepName)
                .map(this::toSignature)
                .orElse(null);
    }

    private StepSignature getEffectiveStepSignature(UUID notebookId, String stepName) {
        return resolveEffectiveConfig(notebookId, stepName)
                .map(this::toSignature)
                .orElse(null);
    }

    private java.util.Optional<PipelineModelConfig> resolveEffectiveConfig(UUID notebookId, String stepName) {
        return configRepository.findByNotebookIdAndStepName(notebookId, stepName)
                .filter(PipelineModelConfig::isActive)
                .or(() -> configRepository.findGlobalConfigForStep(stepName));
    }

    private StepSignature toSignature(PipelineModelConfig config) {
        Map<String, Object> params = config.getParameters() == null ? Map.of() : Map.copyOf(config.getParameters());
        return new StepSignature(config.getProviderName(), config.getModelName(), params);
    }

    private int enqueueGlobalReprocessing(boolean extractionChanged, boolean embeddingChanged) {
        int queued = 0;
        List<UUID> notebookIds = documentRepository.findNotebookIdsWithDocuments();
        for (UUID notebookId : notebookIds) {
            boolean notebookOverridesExtraction = configRepository
                    .findByNotebookIdAndStepName(notebookId, STEP_EXTRACTION)
                    .filter(PipelineModelConfig::isActive)
                    .isPresent();
            boolean notebookOverridesEmbedding = configRepository
                    .findByNotebookIdAndStepName(notebookId, STEP_EMBEDDING)
                    .filter(PipelineModelConfig::isActive)
                    .isPresent();

            boolean extractionAffectsNotebook = extractionChanged && !notebookOverridesExtraction;
            boolean embeddingAffectsNotebook = embeddingChanged && !notebookOverridesEmbedding;
            if (extractionAffectsNotebook || embeddingAffectsNotebook) {
                queued += enqueueNotebookReprocessing(notebookId, "global_pipeline_config_updated");
            }
        }
        return queued;
    }

    private int enqueueNotebookReprocessing(UUID notebookId, String reason) {
        List<Document> docs = documentRepository.findAllByNotebookId(notebookId);
        if (docs.isEmpty()) {
            return 0;
        }

        Map<String, Object> configOverride = buildEffectiveIngestionConfig(notebookId);
        int queued = 0;
        for (Document doc : docs) {
            if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
                continue;
            }
            doc.setStatus("PROCESSING");
            ingestionService.enqueueReprocessing(doc, configOverride, reason);
            queued++;
        }
        return queued;
    }

    private Map<String, Object> buildEffectiveIngestionConfig(UUID notebookId) {
        Map<String, Object> config = new HashMap<>();
        resolveEffectiveConfig(notebookId, STEP_EXTRACTION)
                .map(PipelineModelConfig::getModelName)
                .ifPresent(model -> config.put("extraction_model", model));
        resolveEffectiveConfig(notebookId, STEP_EMBEDDING)
                .map(PipelineModelConfig::getModelName)
                .ifPresent(model -> config.put("embedding_model", model));
        return config;
    }

    private record StepSignature(String providerName, String modelName, Map<String, Object> parameters) {
    }
}
