package org.sirohi.smartnotebook.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for local Ollama model information.
 */
public record LocalModelResponse(
    UUID id,
    String modelName,
    String displayName,
    String modelType,           // "llm" or "embedding"
    String description,
    Long sizeBytes,
    String humanReadableSize,   // "4.7 GB"
    String parameterCount,      // "7B", "13B"
    Integer minRamGb,
    Integer recommendedRamGb,
    Integer contextLength,
    boolean installed,
    boolean recommended,
    String installStatus,       // "not_installed", "installing", "installed", "failed"
    Integer installProgress,    // 0-100
    OffsetDateTime installedAt
) {
    /**
     * Create from entity.
     */
    public static LocalModelResponse from(org.sirohi.smartnotebook.model.LocalModel model) {
        return new LocalModelResponse(
            model.getId(),
            model.getModelName(),
            model.getDisplayName(),
            model.getModelType(),
            model.getDescription(),
            model.getSizeBytes(),
            model.getHumanReadableSize(),
            model.getParameterCount(),
            model.getMinRamGb(),
            model.getRecommendedRamGb(),
            model.getContextLength(),
            model.isInstalled(),
            model.isRecommended(),
            model.getInstallStatus(),
            model.getInstallProgress(),
            model.getInstalledAt()
        );
    }
}
