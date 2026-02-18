package org.sirohi.smartnotebook.controller;

import jakarta.validation.Valid;
import org.sirohi.smartnotebook.dto.CredentialUpdateRequest;
import org.sirohi.smartnotebook.dto.ProviderStatusResponse;
import org.sirohi.smartnotebook.dto.ProviderTestResponse;
import org.sirohi.smartnotebook.service.ProviderCredentialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing provider credentials.
 *
 * Endpoints:
 * - GET /api/providers - List all providers with status
 * - GET /api/providers/{provider} - Get specific provider status
 * - PUT /api/providers/{provider}/credentials - Add/update API key
 * - DELETE /api/providers/{provider}/credentials - Remove API key
 * - POST /api/providers/{provider}/test - Test API key validity
 * - PATCH /api/providers/{provider}/enable - Enable/disable provider
 */
@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderCredentialService credentialService;

    public ProviderController(ProviderCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /**
     * List all providers with their status (keys masked).
     */
    @GetMapping
    public List<ProviderStatusResponse> listProviders() {
        return credentialService.listProviders();
    }

    /**
     * Get a specific provider's status.
     */
    @GetMapping("/{provider}")
    public ProviderStatusResponse getProvider(@PathVariable String provider) {
        return credentialService.getProvider(provider);
    }

    /**
     * Add or update credentials for a provider.
     */
    @PutMapping("/{provider}/credentials")
    public ProviderStatusResponse updateCredentials(
            @PathVariable String provider,
            @Valid @RequestBody CredentialUpdateRequest request) {
        return credentialService.updateCredentials(provider, request.apiKey(), request.baseUrl());
    }

    /**
     * Delete credentials for a provider.
     */
    @DeleteMapping("/{provider}/credentials")
    public ResponseEntity<Void> deleteCredentials(@PathVariable String provider) {
        credentialService.deleteCredentials(provider);
        return ResponseEntity.noContent().build();
    }

    /**
     * Test credentials for a provider.
     */
    @PostMapping("/{provider}/test")
    public ProviderTestResponse testCredentials(@PathVariable String provider) {
        return credentialService.testCredentials(provider);
    }

    /**
     * Enable or disable a provider.
     */
    @PatchMapping("/{provider}/enable")
    public ProviderStatusResponse setEnabled(
            @PathVariable String provider,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        return credentialService.setEnabled(provider, enabled);
    }

    /**
     * Check if a provider is available for use.
     */
    @GetMapping("/{provider}/available")
    public ResponseEntity<Map<String, Boolean>> isProviderAvailable(@PathVariable String provider) {
        boolean available = credentialService.isProviderAvailable(provider);
        return ResponseEntity.ok(Map.of("available", available));
    }
}
