package org.sirohi.smartnotebook.controller;

import jakarta.validation.Valid;
import org.sirohi.smartnotebook.dto.QuotaLimitRequest;
import org.sirohi.smartnotebook.dto.QuotaStatusResponse;
import org.sirohi.smartnotebook.service.QuotaService;
import org.sirohi.smartnotebook.service.QuotaService.UsageDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing API usage quotas.
 *
 * Endpoints:
 * - GET /api/quotas - Get all quota status
 * - GET /api/quotas/{provider} - Get provider quota status
 * - PUT /api/quotas/{provider} - Set quota limits
 * - DELETE /api/quotas/{provider} - Remove quota limits
 * - POST /api/quotas/{provider}/reset - Reset usage counters
 * - GET /api/quotas/{provider}/usage - Get detailed usage
 */
@RestController
@RequestMapping("/api/quotas")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    /**
     * Get quota status for all providers.
     */
    @GetMapping
    public List<QuotaStatusResponse> getAllQuotaStatus() {
        return quotaService.getAllQuotaStatus();
    }

    /**
     * Get quota status for a specific provider.
     */
    @GetMapping("/{provider}")
    public QuotaStatusResponse getQuotaStatus(@PathVariable String provider) {
        return quotaService.getQuotaStatus(provider);
    }

    /**
     * Set quota limits for a provider.
     */
    @PutMapping("/{provider}")
    public QuotaStatusResponse setQuotaLimits(
            @PathVariable String provider,
            @Valid @RequestBody QuotaLimitRequest request) {
        return quotaService.setQuotaLimits(provider, request);
    }

    /**
     * Delete all quota limits for a provider.
     */
    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> deleteQuotaLimits(@PathVariable String provider) {
        quotaService.deleteQuotaLimits(provider);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reset quota usage for a provider.
     */
    @PostMapping("/{provider}/reset")
    public QuotaStatusResponse resetQuotaUsage(@PathVariable String provider) {
        return quotaService.resetQuotaUsage(provider);
    }

    /**
     * Get detailed usage for a provider.
     */
    @GetMapping("/{provider}/usage")
    public UsageDetails getUsageDetails(
            @PathVariable String provider,
            @RequestParam(defaultValue = "30") int days) {
        return quotaService.getUsageDetails(provider, days);
    }

    /**
     * Check if quota is available for a provider.
     */
    @GetMapping("/{provider}/check")
    public ResponseEntity<QuotaCheckResponse> checkQuota(@PathVariable String provider) {
        boolean available = quotaService.hasAvailableQuota(provider);
        return ResponseEntity.ok(new QuotaCheckResponse(provider, available));
    }

    /**
     * Simple quota check response.
     */
    public record QuotaCheckResponse(String provider, boolean available) {}
}
