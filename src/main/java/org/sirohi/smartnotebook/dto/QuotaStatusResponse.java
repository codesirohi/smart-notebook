package org.sirohi.smartnotebook.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response DTO for quota status information.
 */
public record QuotaStatusResponse(
    String providerName,
    List<QuotaLimitInfo> limits,
    boolean isBlocked,          // true if any limit exceeded
    String blockReason,
    OffsetDateTime blockedUntil
) {
    /**
     * Individual quota limit information.
     */
    public record QuotaLimitInfo(
        String limitType,           // "daily_tokens", "monthly_cost_usd", "requests_per_minute"
        long limitValue,
        long currentUsage,
        int usagePercentage,
        long remaining,
        boolean isExceeded,
        OffsetDateTime resetAt,
        boolean enabled
    ) {
        public static QuotaLimitInfo from(org.sirohi.smartnotebook.model.QuotaLimit limit) {
            return new QuotaLimitInfo(
                limit.getLimitType(),
                limit.getLimitValue(),
                limit.getCurrentUsage(),
                limit.getUsagePercentage(),
                limit.getRemaining(),
                limit.isExceeded(),
                limit.getResetAt(),
                limit.isEnabled()
            );
        }
    }

    /**
     * Create response with no limits configured.
     */
    public static QuotaStatusResponse noLimits(String providerName) {
        return new QuotaStatusResponse(
            providerName,
            List.of(),
            false,
            null,
            null
        );
    }

    /**
     * Create response for blocked provider.
     */
    public static QuotaStatusResponse blocked(String providerName, List<QuotaLimitInfo> limits,
                                               String reason, OffsetDateTime blockedUntil) {
        return new QuotaStatusResponse(
            providerName,
            limits,
            true,
            reason,
            blockedUntil
        );
    }
}
