package org.sirohi.smartnotebook.dto;

import java.util.List;

/**
 * Response DTO for provider credential test results.
 */
public record ProviderTestResponse(
    String providerName,
    boolean success,
    String message,
    Integer latencyMs,
    List<String> availableModels
) {
    /**
     * Create success response.
     */
    public static ProviderTestResponse success(String providerName, int latencyMs, List<String> models) {
        return new ProviderTestResponse(
            providerName,
            true,
            "API key is valid",
            latencyMs,
            models
        );
    }

    /**
     * Create failure response.
     */
    public static ProviderTestResponse failure(String providerName, String errorMessage) {
        return new ProviderTestResponse(
            providerName,
            false,
            errorMessage,
            null,
            List.of()
        );
    }
}
