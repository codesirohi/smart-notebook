package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.QuotaLimitRequest;
import org.sirohi.smartnotebook.dto.QuotaStatusResponse;
import org.sirohi.smartnotebook.dto.QuotaStatusResponse.QuotaLimitInfo;
import org.sirohi.smartnotebook.exception.QuotaExceededException;
import org.sirohi.smartnotebook.exception.ResourceNotFoundException;
import org.sirohi.smartnotebook.model.ApiUsage;
import org.sirohi.smartnotebook.model.QuotaLimit;
import org.sirohi.smartnotebook.repository.ApiUsageRepository;
import org.sirohi.smartnotebook.repository.ProviderCredentialRepository;
import org.sirohi.smartnotebook.repository.QuotaLimitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing API usage quotas and tracking.
 *
 * Features:
 * - Configure quota limits per provider
 * - Track API usage (tokens, cost, requests)
 * - Check quota before API calls
 * - Auto-reset quotas when window expires
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    // Pricing per 1M tokens (input / output)
    private static final Map<String, double[]> MODEL_PRICING = Map.of(
            "gpt-4", new double[]{30.0, 60.0},
            "gpt-4-turbo", new double[]{10.0, 30.0},
            "gpt-3.5-turbo", new double[]{0.5, 1.5},
            "claude-3-opus", new double[]{15.0, 75.0},
            "claude-3-sonnet", new double[]{3.0, 15.0},
            "claude-3-haiku", new double[]{0.25, 1.25},
            "gemini-1.5-pro", new double[]{1.25, 5.0},
            "gemini-1.5-flash", new double[]{0.075, 0.3}
    );

    private final QuotaLimitRepository quotaRepository;
    private final ApiUsageRepository usageRepository;
    private final ProviderCredentialRepository credentialRepository;

    public QuotaService(QuotaLimitRepository quotaRepository,
                       ApiUsageRepository usageRepository,
                       ProviderCredentialRepository credentialRepository) {
        this.quotaRepository = quotaRepository;
        this.usageRepository = usageRepository;
        this.credentialRepository = credentialRepository;
    }

    /**
     * Get quota status for all providers.
     */
    @Transactional(readOnly = true)
    public List<QuotaStatusResponse> getAllQuotaStatus() {
        // Reset expired quotas first
        resetExpiredQuotas();

        return credentialRepository.findAll().stream()
                .map(cred -> getQuotaStatus(cred.getProviderName()))
                .toList();
    }

    /**
     * Get quota status for a specific provider.
     */
    @Transactional(readOnly = true)
    public QuotaStatusResponse getQuotaStatus(String providerName) {
        List<QuotaLimit> limits = quotaRepository.findByProviderName(providerName);

        if (limits.isEmpty()) {
            return QuotaStatusResponse.noLimits(providerName);
        }

        List<QuotaLimitInfo> limitInfos = limits.stream()
                .map(QuotaLimitInfo::from)
                .toList();

        // Check if any limit is exceeded
        QuotaLimit exceededLimit = limits.stream()
                .filter(QuotaLimit::isExceeded)
                .findFirst()
                .orElse(null);

        if (exceededLimit != null) {
            return QuotaStatusResponse.blocked(
                    providerName,
                    limitInfos,
                    "Quota exceeded: " + exceededLimit.getLimitType(),
                    exceededLimit.getResetAt()
            );
        }

        return new QuotaStatusResponse(
                providerName,
                limitInfos,
                false,
                null,
                null
        );
    }

    /**
     * Set quota limits for a provider.
     */
    @Transactional
    public QuotaStatusResponse setQuotaLimits(String providerName, QuotaLimitRequest request) {
        if (!credentialRepository.existsByProviderName(providerName)) {
            throw new ResourceNotFoundException("Provider not found: " + providerName);
        }

        log.info("Setting quota limits for provider: {}", providerName);

        // Daily tokens limit
        if (request.dailyTokens() != null) {
            setLimit(providerName, QuotaLimit.DAILY_TOKENS, request.dailyTokens(),
                    QuotaLimit.WINDOW_DAILY, request.alertThresholdOrDefault());
        }

        // Monthly cost limit
        if (request.monthlyCostUsd() != null) {
            // Convert to millicents for precision
            long costValue = request.monthlyCostUsd().multiply(BigDecimal.valueOf(100000)).longValue();
            setLimit(providerName, QuotaLimit.MONTHLY_COST_USD, costValue,
                    QuotaLimit.WINDOW_MONTHLY, request.alertThresholdOrDefault());
        }

        // Requests per minute limit
        if (request.requestsPerMinute() != null) {
            setLimit(providerName, QuotaLimit.REQUESTS_PER_MINUTE, request.requestsPerMinute(),
                    QuotaLimit.WINDOW_MINUTE, request.alertThresholdOrDefault());
        }

        return getQuotaStatus(providerName);
    }

    /**
     * Delete all quota limits for a provider.
     */
    @Transactional
    public void deleteQuotaLimits(String providerName) {
        log.info("Deleting quota limits for provider: {}", providerName);
        quotaRepository.deleteByProviderName(providerName);
    }

    /**
     * Reset quota usage for a provider.
     */
    @Transactional
    public QuotaStatusResponse resetQuotaUsage(String providerName) {
        log.info("Resetting quota usage for provider: {}", providerName);

        List<QuotaLimit> limits = quotaRepository.findByProviderName(providerName);
        for (QuotaLimit limit : limits) {
            limit.reset();
            quotaRepository.save(limit);
        }

        return getQuotaStatus(providerName);
    }

    /**
     * Check if quota is available for a provider.
     * Throws QuotaExceededException if not.
     */
    @Transactional
    public void checkQuota(String providerName) {
        // Reset expired quotas first
        resetExpiredQuotasForProvider(providerName);

        List<QuotaLimit> exceededQuotas = quotaRepository.findExceededQuotas(providerName);

        if (!exceededQuotas.isEmpty()) {
            QuotaLimit exceeded = exceededQuotas.get(0);
            throw new QuotaExceededException(
                    providerName,
                    exceeded.getLimitType(),
                    exceeded.getResetAt()
            );
        }
    }

    /**
     * Check if quota is available without throwing.
     */
    @Transactional(readOnly = true)
    public boolean hasAvailableQuota(String providerName) {
        return !quotaRepository.hasExceededQuota(providerName);
    }

    /**
     * Record API usage.
     */
    @Transactional
    public void recordUsage(String providerName, String modelName, String operation,
                           int tokensInput, int tokensOutput, int latencyMs,
                           boolean success, UUID notebookId, UUID chatId) {

        // Calculate estimated cost
        BigDecimal cost = estimateCost(modelName, tokensInput, tokensOutput);

        // Create usage record
        ApiUsage usage = ApiUsage.builder()
                .provider(providerName)
                .model(modelName)
                .operation(operation)
                .tokens(tokensInput, tokensOutput)
                .cost(cost)
                .latency(latencyMs)
                .success(success)
                .notebook(notebookId)
                .chat(chatId)
                .build();

        usageRepository.save(usage);

        // Update quota usage
        updateQuotaUsage(providerName, tokensInput + tokensOutput, cost);

        log.debug("Recorded usage for {}/{}: {} tokens, ${}", providerName, modelName,
                tokensInput + tokensOutput, cost);
    }

    /**
     * Get usage details for a provider.
     */
    @Transactional(readOnly = true)
    public UsageDetails getUsageDetails(String providerName, int days) {
        OffsetDateTime since = OffsetDateTime.now().minusDays(days);

        Long totalTokens = usageRepository.getDailyTokenUsage(providerName);
        BigDecimal monthlyCost = usageRepository.getMonthlyCost(providerName);
        Double avgLatency = usageRepository.getAverageLatency(providerName, since);
        Long errorCount = usageRepository.getErrorCount(providerName, since);

        List<Object[]> dailyBreakdown = usageRepository.getDailyBreakdown(providerName, since);
        List<Object[]> byModel = usageRepository.getUsageByModel(providerName, since);

        return new UsageDetails(
                providerName,
                totalTokens != null ? totalTokens : 0,
                monthlyCost != null ? monthlyCost : BigDecimal.ZERO,
                avgLatency != null ? avgLatency.intValue() : 0,
                errorCount != null ? errorCount : 0,
                dailyBreakdown,
                byModel
        );
    }

    // ─── Private Helpers ───

    private void setLimit(String providerName, String limitType, long limitValue,
                         int windowSeconds, int alertThreshold) {
        QuotaLimit limit = quotaRepository.findByProviderNameAndLimitType(providerName, limitType)
                .orElseGet(() -> {
                    QuotaLimit newLimit = new QuotaLimit();
                    newLimit.setProviderName(providerName);
                    newLimit.setLimitType(limitType);
                    return newLimit;
                });

        limit.setLimitValue(limitValue);
        limit.setWindowSeconds(windowSeconds);
        limit.setAlertThresholdPct(alertThreshold);
        limit.setEnabled(true);

        if (limit.getResetAt() == null) {
            limit.calculateResetAt();
        }

        quotaRepository.save(limit);
        log.debug("Set {} limit for {}: {} (window: {}s)", limitType, providerName, limitValue, windowSeconds);
    }

    private void updateQuotaUsage(String providerName, int totalTokens, BigDecimal cost) {
        // Update token-based limits
        quotaRepository.incrementUsage(providerName, QuotaLimit.DAILY_TOKENS, totalTokens);

        // Update cost-based limits (convert to millicents)
        if (cost != null && cost.compareTo(BigDecimal.ZERO) > 0) {
            long costMillicents = cost.multiply(BigDecimal.valueOf(100000)).longValue();
            quotaRepository.incrementUsage(providerName, QuotaLimit.MONTHLY_COST_USD, costMillicents);
        }

        // Update request count
        quotaRepository.incrementUsage(providerName, QuotaLimit.REQUESTS_PER_MINUTE, 1);
    }

    private BigDecimal estimateCost(String modelName, int tokensInput, int tokensOutput) {
        // Normalize model name (remove provider prefix if present)
        String normalizedName = modelName.toLowerCase();
        for (String key : MODEL_PRICING.keySet()) {
            if (normalizedName.contains(key.toLowerCase())) {
                double[] pricing = MODEL_PRICING.get(key);
                double inputCost = (tokensInput / 1_000_000.0) * pricing[0];
                double outputCost = (tokensOutput / 1_000_000.0) * pricing[1];
                return BigDecimal.valueOf(inputCost + outputCost);
            }
        }
        // Unknown model - no cost tracking
        return BigDecimal.ZERO;
    }

    private void resetExpiredQuotas() {
        List<QuotaLimit> expired = quotaRepository.findExpiredQuotas();
        for (QuotaLimit quota : expired) {
            quota.reset();
            quotaRepository.save(quota);
            log.debug("Reset expired quota: {}/{}", quota.getProviderName(), quota.getLimitType());
        }
    }

    private void resetExpiredQuotasForProvider(String providerName) {
        List<QuotaLimit> limits = quotaRepository.findByProviderNameAndEnabledTrue(providerName);
        for (QuotaLimit limit : limits) {
            if (limit.needsReset()) {
                limit.reset();
                quotaRepository.save(limit);
                log.debug("Reset expired quota: {}/{}", providerName, limit.getLimitType());
            }
        }
    }

    /**
     * Usage details record for a provider.
     */
    public record UsageDetails(
            String providerName,
            long totalTokensToday,
            BigDecimal monthlyCostUsd,
            int avgLatencyMs,
            long errorCount,
            List<Object[]> dailyBreakdown,
            List<Object[]> byModel
    ) {}
}
