package org.sirohi.smartnotebook.dto;

import java.math.BigDecimal;

/**
 * Request DTO for setting quota limits.
 * All fields are optional - only set limits you want to configure.
 */
public record QuotaLimitRequest(
    Long dailyTokens,           // Max tokens per day
    BigDecimal monthlyCostUsd,  // Max cost in USD per month
    Integer requestsPerMinute,  // Max requests per minute (rate limit)
    Integer alertThresholdPct   // Alert when usage exceeds this % (default: 80)
) {
    /**
     * Check if any limit is configured.
     */
    public boolean hasAnyLimit() {
        return dailyTokens != null || monthlyCostUsd != null || requestsPerMinute != null;
    }

    /**
     * Get alert threshold with default.
     */
    public int alertThresholdOrDefault() {
        return alertThresholdPct != null ? alertThresholdPct : 80;
    }
}
