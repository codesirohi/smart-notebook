package org.sirohi.smartnotebook.exception;

import java.time.OffsetDateTime;

/**
 * Exception thrown when API quota is exceeded.
 * Includes reset time for rate limit headers.
 */
public class QuotaExceededException extends RuntimeException {

    private final String providerName;
    private final String limitType;
    private final OffsetDateTime resetAt;

    public QuotaExceededException(String providerName, String limitType, OffsetDateTime resetAt) {
        super(String.format("Quota exceeded for %s: %s. Resets at %s", providerName, limitType, resetAt));
        this.providerName = providerName;
        this.limitType = limitType;
        this.resetAt = resetAt;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getLimitType() {
        return limitType;
    }

    public OffsetDateTime getResetAt() {
        return resetAt;
    }
}
