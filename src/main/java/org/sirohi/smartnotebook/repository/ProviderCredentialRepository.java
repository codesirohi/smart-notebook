package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.model.ProviderCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderCredentialRepository extends JpaRepository<ProviderCredential, UUID> {

    /**
     * Find credential by provider name.
     */
    Optional<ProviderCredential> findByProviderName(String providerName);

    /**
     * Check if provider exists.
     */
    boolean existsByProviderName(String providerName);

    /**
     * Find all enabled providers.
     */
    List<ProviderCredential> findByEnabledTrue();

    /**
     * Find all providers with credentials configured.
     */
    @Query("SELECT p FROM ProviderCredential p WHERE p.encryptedApiKey IS NOT NULL")
    List<ProviderCredential> findAllWithCredentials();

    /**
     * Update validation status.
     */
    @Modifying
    @Query("UPDATE ProviderCredential p SET p.validationStatus = :status, p.lastValidatedAt = :timestamp WHERE p.providerName = :providerName")
    void updateValidationStatus(String providerName, String status, OffsetDateTime timestamp);

    /**
     * Enable or disable a provider.
     */
    @Modifying
    @Query("UPDATE ProviderCredential p SET p.enabled = :enabled, p.updatedAt = CURRENT_TIMESTAMP WHERE p.providerName = :providerName")
    void setEnabled(String providerName, boolean enabled);

    /**
     * Clear credentials for a provider.
     */
    @Modifying
    @Query("UPDATE ProviderCredential p SET p.encryptedApiKey = NULL, p.validationStatus = 'unknown', p.updatedAt = CURRENT_TIMESTAMP WHERE p.providerName = :providerName")
    void clearCredentials(String providerName);
}
