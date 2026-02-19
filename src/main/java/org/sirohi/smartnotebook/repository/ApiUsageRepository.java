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
    @Query(value = """
        SELECT COALESCE(SUM(u.tokens_input + u.tokens_output), 0)
        FROM api_usage u
        WHERE u.provider_name = :providerName
          AND u.request_timestamp >= CURRENT_DATE
        """, nativeQuery = true)
    Long getDailyTokenUsage(String providerName);

    /**
     * Get total cost this month for a provider.
     */
    @Query(value = """
        SELECT COALESCE(SUM(u.estimated_cost_usd), 0)
        FROM api_usage u
        WHERE u.provider_name = :providerName
          AND DATE_TRUNC('month', u.request_timestamp) = DATE_TRUNC('month', CURRENT_DATE)
        """, nativeQuery = true)
    BigDecimal getMonthlyCost(String providerName);

    /**
     * Get request count in last minute for a provider (rate limiting).
     */
    @Query(value = """
        SELECT COUNT(*)
        FROM api_usage u
        WHERE u.provider_name = :providerName
          AND u.request_timestamp > :oneMinuteAgo
        """, nativeQuery = true)
    Long getRequestsInLastMinute(String providerName, OffsetDateTime oneMinuteAgo);

    /**
     * Get usage summary by provider for a time period.
     */
    @Query(value = """
        SELECT u.provider_name,
               COALESCE(SUM(u.tokens_input), 0),
               COALESCE(SUM(u.tokens_output), 0),
               COALESCE(SUM(u.estimated_cost_usd), 0),
               COUNT(*),
               SUM(CASE WHEN u.success = false THEN 1 ELSE 0 END)
        FROM api_usage u
        WHERE u.request_timestamp > :since
        GROUP BY u.provider_name
        """, nativeQuery = true)
    List<Object[]> getUsageSummaryByProvider(OffsetDateTime since);

    /**
     * Get usage summary by model for a provider.
     */
    @Query(value = """
        SELECT u.model_name,
               COALESCE(SUM(u.tokens_input + u.tokens_output), 0),
               COALESCE(SUM(u.estimated_cost_usd), 0),
               COUNT(*)
        FROM api_usage u
        WHERE u.provider_name = :providerName
          AND u.request_timestamp > :since
        GROUP BY u.model_name
        """, nativeQuery = true)
    List<Object[]> getUsageByModel(String providerName, OffsetDateTime since);

    /**
     * Get usage summary by operation for a provider.
     */
    @Query(value = """
        SELECT u.operation,
               COALESCE(SUM(u.tokens_input + u.tokens_output), 0),
               COALESCE(SUM(u.estimated_cost_usd), 0),
               COUNT(*)
        FROM api_usage u
        WHERE u.provider_name = :providerName
          AND u.request_timestamp > :since
        GROUP BY u.operation
        """, nativeQuery = true)
    List<Object[]> getUsageByOperation(String providerName, OffsetDateTime since);

    /**
     * Get daily usage breakdown for a provider.
     */
    @Query(value = """
        SELECT DATE(u.request_timestamp),
               COALESCE(SUM(u.tokens_input + u.tokens_output), 0),
               COALESCE(SUM(u.estimated_cost_usd), 0),
               COUNT(*)
        FROM api_usage u
        WHERE u.provider_name = :providerName
          AND u.request_timestamp > :since
        GROUP BY DATE(u.request_timestamp)
        ORDER BY DATE(u.request_timestamp)
        """, nativeQuery = true)
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
    @Query(value = """
        SELECT COALESCE(AVG(u.latency_ms), 0)
        FROM api_usage u
        WHERE u.provider_name = :providerName
          AND u.success = true
          AND u.request_timestamp > :since
        """, nativeQuery = true)
    Double getAverageLatency(String providerName, OffsetDateTime since);

    /**
     * Get error count by provider.
     */
    @Query(value = """
        SELECT COUNT(*)
        FROM api_usage u
        WHERE u.provider_name = :providerName
          AND u.success = false
          AND u.request_timestamp > :since
        """, nativeQuery = true)
    Long getErrorCount(String providerName, OffsetDateTime since);
}
