package org.sirohi.smartnotebook.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request DTO for updating pipeline model configuration.
 */
public record PipelineConfigRequest(
    @Valid StepConfigRequest extraction,
    @Valid StepConfigRequest embedding,
    @Valid StepConfigRequest chat
) {
    /**
     * Step configuration request.
     */
    public record StepConfigRequest(
        @NotBlank(message = "Provider name is required")
        String providerName,

        @NotBlank(message = "Model name is required")
        String modelName,

        Map<String, Object> parameters
    ) {
        public Map<String, Object> parametersOrDefault() {
            return parameters != null ? parameters : Map.of();
        }
    }
}
