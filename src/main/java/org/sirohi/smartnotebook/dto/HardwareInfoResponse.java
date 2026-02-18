package org.sirohi.smartnotebook.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO for hardware information.
 */
public record HardwareInfoResponse(
    int totalRamGb,
    int availableRamGb,
    int cpuCores,
    String cpuModel,
    String gpuName,
    Integer gpuVramGb,
    String osName,
    String osVersion,
    String recommendedTier,     // "minimal", "standard", "performance", "high_end"
    OffsetDateTime lastCheckedAt
) {
    /**
     * Create from entity.
     */
    public static HardwareInfoResponse from(org.sirohi.smartnotebook.model.HardwareInfo info) {
        return new HardwareInfoResponse(
            info.getTotalRamGb() != null ? info.getTotalRamGb() : 0,
            info.getAvailableRamGb() != null ? info.getAvailableRamGb() : 0,
            info.getCpuCores() != null ? info.getCpuCores() : 0,
            info.getCpuModel(),
            info.getGpuName(),
            info.getGpuVramGb(),
            info.getOsName(),
            info.getOsVersion(),
            info.getRecommendedTier(),
            info.getLastCheckedAt()
        );
    }

    /**
     * Get tier description.
     */
    public String getTierDescription() {
        return switch (recommendedTier) {
            case "minimal" -> "Basic models only (tinyllama, all-minilm). Limited RAM.";
            case "standard" -> "Most common models (phi3, llama3.2, nomic-embed-text). Good for development.";
            case "performance" -> "Larger models (llama3:8b, mistral, codellama). Great for production use.";
            case "high_end" -> "All models including large ones (mixtral, llama3:70b). Maximum quality.";
            default -> "Unknown hardware tier.";
        };
    }
}
