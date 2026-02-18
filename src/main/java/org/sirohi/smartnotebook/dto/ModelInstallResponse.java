package org.sirohi.smartnotebook.dto;

/**
 * Response DTO for model installation operations.
 */
public record ModelInstallResponse(
    String modelName,
    String status,              // "started", "already_installed", "not_found"
    String message
) {
    /**
     * Create response for installation started.
     */
    public static ModelInstallResponse started(String modelName) {
        return new ModelInstallResponse(
            modelName,
            "started",
            "Model installation started. Monitor progress via /api/models/local/" + modelName + "/status"
        );
    }

    /**
     * Create response for already installed.
     */
    public static ModelInstallResponse alreadyInstalled(String modelName) {
        return new ModelInstallResponse(
            modelName,
            "already_installed",
            "Model is already installed"
        );
    }

    /**
     * Create response for model not found in curated list.
     */
    public static ModelInstallResponse notFound(String modelName) {
        return new ModelInstallResponse(
            modelName,
            "not_found",
            "Model not found in curated list. Use Ollama CLI to install custom models."
        );
    }

    /**
     * Create response for installation failed.
     */
    public static ModelInstallResponse failed(String modelName, String reason) {
        return new ModelInstallResponse(
            modelName,
            "failed",
            reason
        );
    }
}
