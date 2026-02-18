package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.model.QuotaLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuotaLimitRepository extends JpaRepository<QuotaLimit, UUID> {

    /**
     * Find all limits for a provider.
     */
    List<QuotaLimit> findByProviderName(String providerName);

    /**
     * Find enabled limits for a provider.
     */
    List<QuotaLimit> findByProviderNameAndEnabledTrue(String providerName);

    /**
     * Find a specific limit type for a provider.
     */
    Optional<QuotaLimit> findByProviderNameAndLimitType(String providerName, String limitType);

    /**
     * Check if provider has any limits configured.
     */
    boolean existsByProviderName(String providerName);

    /**
     * Delete all limits for a provider.
     */
    @Modifying
    void deleteByProviderName(String providerName);

    /**
     * Update current usage for a limit.
     */
    @Modifying
    @Query("UPDATE QuotaLimit q SET q.currentUsage = :usage, q.updatedAt = CURRENT_TIMESTAMP WHERE q.id = :id")
    void updateUsage(UUID id, long usage);

    /**
     * Increment current usage for a limit.
     */
    @Modifying
    @Query("UPDATE QuotaLimit q SET q.currentUsage = q.currentUsage + :delta, q.updatedAt = CURRENT_TIMESTAMP WHERE q.providerName = :providerName AND q.limitType = :limitType")
    void incrementUsage(String providerName, String limitType, long delta);

    /**
     * Reset usage for expired quotas.
     */
    @Modifying
    @Query("UPDATE QuotaLimit q SET q.currentUsage = 0, q.resetAt = :newResetAt, q.updatedAt = CURRENT_TIMESTAMP WHERE q.resetAt < :now AND q.enabled = true")
    int resetExpiredQuotas(OffsetDateTime now, OffsetDateTime newResetAt);

    /**
     * Find quotas that need reset (past their reset time).
     */
    @Query("SELECT q FROM QuotaLimit q WHERE q.resetAt < CURRENT_TIMESTAMP AND q.enabled = true")
    List<QuotaLimit> findExpiredQuotas();

    /**
     * Find quotas exceeding alert threshold.
     */
    @Query("SELECT q FROM QuotaLimit q WHERE q.enabled = true AND (q.currentUsage * 100 / q.limitValue) >= q.alertThresholdPct")
    List<QuotaLimit> findQuotasExceedingThreshold();

    /**
     * Find exceeded quotas for a provider.
     */
    @Query("SELECT q FROM QuotaLimit q WHERE q.providerName = :providerName AND q.enabled = true AND q.currentUsage >= q.limitValue")
    List<QuotaLimit> findExceededQuotas(String providerName);

    /**
     * Check if any quota is exceeded for a provider.
     */
    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END FROM QuotaLimit q WHERE q.providerName = :providerName AND q.enabled = true AND q.currentUsage >= q.limitValue")
    boolean hasExceededQuota(String providerName);

    /**
     * Get all enabled quotas.
     */
    List<QuotaLimit> findByEnabledTrue();
}
