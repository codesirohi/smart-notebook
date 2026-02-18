package org.sirohi.smartnotebook.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating provider credentials.
 */
public record CredentialUpdateRequest(
    @NotBlank(message = "API key is required")
    String apiKey,

    String baseUrl  // Optional custom endpoint
) {}
