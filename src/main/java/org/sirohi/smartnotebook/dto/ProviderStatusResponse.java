package org.sirohi.smartnotebook.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response DTO for provider status information.
 */
public record ProviderStatusResponse(
    String providerName,
    String displayName,
    boolean enabled,
    boolean hasCredentials,
    String maskedApiKey,        // "sk-...xxxx" or null
    String validationStatus,    // "valid", "invalid", "unknown"
    OffsetDateTime lastValidatedAt,
    String baseUrl,
    List<String> supportedOperations  // ["completion", "embedding", "streaming"]
) {
    /**
     * Create from entity with masked key.
     */
    public static ProviderStatusResponse from(
            org.sirohi.smartnotebook.model.ProviderCredential cred,
            String maskedKey,
            List<String> supportedOps) {
        return new ProviderStatusResponse(
            cred.getProviderName(),
            cred.getDisplayName(),
            cred.isEnabled(),
            cred.hasCredentials(),
            maskedKey,
            cred.getValidationStatus(),
            cred.getLastValidatedAt(),
            cred.getBaseUrl(),
            supportedOps
        );
    }

    /**
     * Get supported operations for a provider.
     */
    public static List<String> getSupportedOperations(String providerName) {
        return switch (providerName.toLowerCase()) {
            case "ollama" -> List.of("completion", "embedding", "streaming");
            case "openai" -> List.of("completion", "embedding", "streaming");
            case "anthropic" -> List.of("completion", "streaming");  // No embeddings
            case "google" -> List.of("completion", "embedding");     // Limited streaming
            case "groq" -> List.of("completion", "streaming");       // No embeddings
            default -> List.of("completion");
        };
    }
}
