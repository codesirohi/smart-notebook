package org.sirohi.smartnotebook.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for pipeline model configuration.
 */
public record PipelineConfigResponse(
    UUID notebookId,            // null for global
    boolean isGlobal,
    StepConfig extraction,
    StepConfig embedding,
    StepConfig chat,
    OffsetDateTime updatedAt
) {
    /**
     * Step configuration for a pipeline step.
     */
    public record StepConfig(
        String providerName,
        String modelName,
        java.util.Map<String, Object> parameters,
        boolean isAvailable     // based on provider status
    ) {
        public static StepConfig from(
                org.sirohi.smartnotebook.model.PipelineModelConfig config,
                boolean available) {
            return new StepConfig(
                config.getProviderName(),
                config.getModelName(),
                config.getParameters(),
                available
            );
        }

        public static StepConfig empty() {
            return new StepConfig(null, null, java.util.Map.of(), false);
        }
    }
}
