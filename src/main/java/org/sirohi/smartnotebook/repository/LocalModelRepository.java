package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.model.LocalModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocalModelRepository extends JpaRepository<LocalModel, UUID> {

    /**
     * Find model by name.
     */
    Optional<LocalModel> findByModelName(String modelName);

    /**
     * Check if model exists.
     */
    boolean existsByModelName(String modelName);

    /**
     * Find all installed models.
     */
    List<LocalModel> findByInstalledTrue();

    /**
     * Find all models of a specific type.
     */
    List<LocalModel> findByModelType(String modelType);

    /**
     * Find installed models of a specific type.
     */
    List<LocalModel> findByModelTypeAndInstalledTrue(String modelType);

    /**
     * Find recommended models.
     */
    List<LocalModel> findByRecommendedTrue();

    /**
     * Find models that can run with given RAM.
     */
    @Query("SELECT m FROM LocalModel m WHERE m.minRamGb <= :availableRamGb ORDER BY m.minRamGb DESC")
    List<LocalModel> findModelsForRam(int availableRamGb);

    /**
     * Find recommended models for given RAM.
     */
    @Query("SELECT m FROM LocalModel m WHERE m.minRamGb <= :availableRamGb AND m.recommended = true ORDER BY m.minRamGb DESC")
    List<LocalModel> findRecommendedModelsForRam(int availableRamGb);

    /**
     * Update install status.
     */
    @Modifying
    @Query("UPDATE LocalModel m SET m.installStatus = :status, m.installProgress = :progress WHERE m.modelName = :modelName")
    void updateInstallStatus(String modelName, String status, int progress);

    /**
     * Mark model as installed.
     */
    @Modifying
    @Query("UPDATE LocalModel m SET m.installed = true, m.installStatus = 'installed', m.installProgress = 100, m.installedAt = :installedAt WHERE m.modelName = :modelName")
    void markInstalled(String modelName, OffsetDateTime installedAt);

    /**
     * Mark model as not installed.
     */
    @Modifying
    @Query("UPDATE LocalModel m SET m.installed = false, m.installStatus = 'not_installed', m.installProgress = 0, m.installedAt = NULL WHERE m.modelName = :modelName")
    void markUninstalled(String modelName);

    /**
     * Update all recommendation flags based on available RAM.
     */
    @Modifying
    @Query("UPDATE LocalModel m SET m.recommended = (m.minRamGb <= :availableRamGb)")
    void updateRecommendations(int availableRamGb);

    /**
     * Update model size after installation.
     */
    @Modifying
    @Query("UPDATE LocalModel m SET m.sizeBytes = :sizeBytes WHERE m.modelName = :modelName")
    void updateSize(String modelName, Long sizeBytes);

    /**
     * Find models currently installing.
     */
    List<LocalModel> findByInstallStatus(String installStatus);
}
