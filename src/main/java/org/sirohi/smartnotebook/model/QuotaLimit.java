package org.sirohi.smartnotebook.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for quota limits configuration.
 * Defines usage limits per provider (daily tokens, monthly cost, etc.)
 */
@Entity
@Table(name = "quota_limits",
       uniqueConstraints = @UniqueConstraint(columnNames = {"provider_name", "limit_type"}))
public class QuotaLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Column(name = "limit_type", nullable = false, length = 30)
    private String limitType;  // 'daily_tokens', 'monthly_cost_usd', 'requests_per_minute'

    @Column(name = "limit_value", nullable = false)
    private long limitValue;

    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds;  // 86400 for daily, 2592000 for monthly, 60 for rpm

    @Column(name = "current_usage")
    private long currentUsage = 0;

    @Column(name = "reset_at")
    private OffsetDateTime resetAt;

    @Column(name = "is_enabled")
    private boolean enabled = true;

    @Column(name = "alert_threshold_pct")
    private int alertThresholdPct = 80;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        calculateResetAt();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /**
     * Calculate when this quota should reset based on window type.
     */
    public void calculateResetAt() {
        OffsetDateTime now = OffsetDateTime.now();
        if (windowSeconds == 86400) {
            // Daily - reset at midnight
            resetAt = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else if (windowSeconds == 2592000) {
            // Monthly - reset at start of next month
            resetAt = now.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else {
            // Default - reset after window_seconds
            resetAt = now.plusSeconds(windowSeconds);
        }
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getLimitType() {
        return limitType;
    }

    public void setLimitType(String limitType) {
        this.limitType = limitType;
    }

    public long getLimitValue() {
        return limitValue;
    }

    public void setLimitValue(long limitValue) {
        this.limitValue = limitValue;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public long getCurrentUsage() {
        return currentUsage;
    }

    public void setCurrentUsage(long currentUsage) {
        this.currentUsage = currentUsage;
    }

    public OffsetDateTime getResetAt() {
        return resetAt;
    }

    public void setResetAt(OffsetDateTime resetAt) {
        this.resetAt = resetAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAlertThresholdPct() {
        return alertThresholdPct;
    }

    public void setAlertThresholdPct(int alertThresholdPct) {
        this.alertThresholdPct = alertThresholdPct;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Check if the quota is exceeded.
     */
    public boolean isExceeded() {
        return enabled && currentUsage >= limitValue;
    }

    /**
     * Get usage percentage (0-100).
     */
    public int getUsagePercentage() {
        if (limitValue == 0) return 0;
        return (int) ((currentUsage * 100) / limitValue);
    }

    /**
     * Get remaining quota.
     */
    public long getRemaining() {
        return Math.max(0, limitValue - currentUsage);
    }

    /**
     * Check if alert threshold is reached.
     */
    public boolean isAlertThresholdReached() {
        return getUsagePercentage() >= alertThresholdPct;
    }

    /**
     * Check if the quota has expired and needs reset.
     */
    public boolean needsReset() {
        return resetAt != null && OffsetDateTime.now().isAfter(resetAt);
    }

    /**
     * Reset the quota usage.
     */
    public void reset() {
        currentUsage = 0;
        calculateResetAt();
        updatedAt = OffsetDateTime.now();
    }

    // Common limit types
    public static final String DAILY_TOKENS = "daily_tokens";
    public static final String MONTHLY_COST_USD = "monthly_cost_usd";
    public static final String REQUESTS_PER_MINUTE = "requests_per_minute";

    // Window durations in seconds
    public static final int WINDOW_DAILY = 86400;
    public static final int WINDOW_MONTHLY = 2592000;
    public static final int WINDOW_MINUTE = 60;
}
