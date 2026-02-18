package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.model.ApiUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApiUsageRepository extends JpaRepository<ApiUsage, UUID> {

    /**
     * Find usage for a provider within a time window.
     */
    List<ApiUsage> findByProviderNameAndRequestTimestampAfter(String providerName, OffsetDateTime after);

    /**
     * Get total tokens used today for a provider.
     */
    @Query("""
        SELECT COALESCE(SUM(u.tokensInput + u.tokensOutput), 0)
        FROM ApiUsage u
        WHERE u.providerName = :providerName
          AND DATE(u.requestTimestamp) = CURRENT_DATE
        """)
    Long getDailyTokenUsage(String providerName);

    /**
     * Get total cost this month for a provider.
     */
    @Query("""
        SELECT COALESCE(SUM(u.estimatedCostUsd), 0)
        FROM ApiUsage u
        WHERE u.providerName = :providerName
          AND DATE_TRUNC('month', u.requestTimestamp) = DATE_TRUNC('month', CURRENT_DATE)
        """)
    BigDecimal getMonthlyCost(String providerName);

    /**
     * Get request count in last minute for a provider (rate limiting).
     */
    @Query("""
        SELECT COUNT(u)
        FROM ApiUsage u
        WHERE u.providerName = :providerName
          AND u.requestTimestamp > :oneMinuteAgo
        """)
    Long getRequestsInLastMinute(String providerName, OffsetDateTime oneMinuteAgo);

    /**
     * Get usage summary by provider for a time period.
     */
    @Query("""
        SELECT u.providerName,
               SUM(u.tokensInput),
               SUM(u.tokensOutput),
               SUM(u.estimatedCostUsd),
               COUNT(u),
               SUM(CASE WHEN u.success = false THEN 1 ELSE 0 END)
        FROM ApiUsage u
        WHERE u.requestTimestamp > :since
        GROUP BY u.providerName
        """)
    List<Object[]> getUsageSummaryByProvider(OffsetDateTime since);

    /**
     * Get usage summary by model for a provider.
     */
    @Query("""
        SELECT u.modelName,
               SUM(u.tokensInput + u.tokensOutput),
               SUM(u.estimatedCostUsd),
               COUNT(u)
        FROM ApiUsage u
        WHERE u.providerName = :providerName
          AND u.requestTimestamp > :since
        GROUP BY u.modelName
        """)
    List<Object[]> getUsageByModel(String providerName, OffsetDateTime since);

    /**
     * Get usage summary by operation for a provider.
     */
    @Query("""
        SELECT u.operation,
               SUM(u.tokensInput + u.tokensOutput),
               SUM(u.estimatedCostUsd),
               COUNT(u)
        FROM ApiUsage u
        WHERE u.providerName = :providerName
          AND u.requestTimestamp > :since
        GROUP BY u.operation
        """)
    List<Object[]> getUsageByOperation(String providerName, OffsetDateTime since);

    /**
     * Get daily usage breakdown for a provider.
     */
    @Query("""
        SELECT DATE(u.requestTimestamp),
               SUM(u.tokensInput + u.tokensOutput),
               SUM(u.estimatedCostUsd),
               COUNT(u)
        FROM ApiUsage u
        WHERE u.providerName = :providerName
          AND u.requestTimestamp > :since
        GROUP BY DATE(u.requestTimestamp)
        ORDER BY DATE(u.requestTimestamp)
        """)
    List<Object[]> getDailyBreakdown(String providerName, OffsetDateTime since);

    /**
     * Get usage for a specific notebook.
     */
    List<ApiUsage> findByNotebookIdAndRequestTimestampAfter(UUID notebookId, OffsetDateTime after);

    /**
     * Get usage for a specific chat.
     */
    List<ApiUsage> findByChatIdOrderByRequestTimestampDesc(UUID chatId);

    /**
     * Get average latency by provider.
     */
    @Query("""
        SELECT COALESCE(AVG(u.latencyMs), 0)
        FROM ApiUsage u
        WHERE u.providerName = :providerName
          AND u.success = true
          AND u.requestTimestamp > :since
        """)
    Double getAverageLatency(String providerName, OffsetDateTime since);

    /**
     * Get error count by provider.
     */
    @Query("""
        SELECT COUNT(u)
        FROM ApiUsage u
        WHERE u.providerName = :providerName
          AND u.success = false
          AND u.requestTimestamp > :since
        """)
    Long getErrorCount(String providerName, OffsetDateTime since);
}
