package org.sirohi.smartnotebook.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for usage summary across providers.
 */
public record UsageSummaryResponse(
    UsagePeriod today,
    UsagePeriod thisWeek,
    UsagePeriod thisMonth,
    List<ProviderUsage> byProvider,
    BigDecimal totalEstimatedCostUsd
) {
    /**
     * Usage statistics for a time period.
     */
    public record UsagePeriod(
        long totalTokens,
        long inputTokens,
        long outputTokens,
        BigDecimal estimatedCostUsd,
        int requestCount,
        int errorCount
    ) {
        public static UsagePeriod empty() {
            return new UsagePeriod(0, 0, 0, BigDecimal.ZERO, 0, 0);
        }
    }

    /**
     * Usage for a specific provider.
     */
    public record ProviderUsage(
        String providerName,
        long totalTokens,
        BigDecimal estimatedCostUsd,
        int requestCount,
        Map<String, Long> byModel,      // model -> tokens
        Map<String, Long> byOperation   // operation -> tokens
    ) {}
}
