package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.model.PipelineModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PipelineModelConfigRepository extends JpaRepository<PipelineModelConfig, UUID> {

    /**
     * Find global config for a step (where notebook_id is NULL).
     */
    @Query("SELECT c FROM PipelineModelConfig c WHERE c.notebookId IS NULL AND c.stepName = :stepName AND c.active = true")
    Optional<PipelineModelConfig> findGlobalConfigForStep(String stepName);

    /**
     * Find all global configs.
     */
    @Query("SELECT c FROM PipelineModelConfig c WHERE c.notebookId IS NULL AND c.active = true")
    List<PipelineModelConfig> findAllGlobalConfigs();

    /**
     * Find config for a notebook and step.
     */
    Optional<PipelineModelConfig> findByNotebookIdAndStepName(UUID notebookId, String stepName);

    /**
     * Find all configs for a notebook.
     */
    List<PipelineModelConfig> findByNotebookIdAndActiveTrue(UUID notebookId);

    /**
     * Find effective config for a step (notebook-specific with global fallback).
     * Returns notebook config if exists, otherwise global config.
     */
    @Query("""
        SELECT c FROM PipelineModelConfig c
        WHERE c.stepName = :stepName
          AND c.active = true
          AND (c.notebookId = :notebookId OR c.notebookId IS NULL)
        ORDER BY c.notebookId NULLS LAST
        """)
    List<PipelineModelConfig> findEffectiveConfig(UUID notebookId, String stepName);

    /**
     * Delete notebook-specific config.
     */
    @Modifying
    @Query("DELETE FROM PipelineModelConfig c WHERE c.notebookId = :notebookId")
    void deleteByNotebookId(UUID notebookId);

    /**
     * Delete notebook-specific config for a step.
     */
    @Modifying
    @Query("DELETE FROM PipelineModelConfig c WHERE c.notebookId = :notebookId AND c.stepName = :stepName")
    void deleteByNotebookIdAndStepName(UUID notebookId, String stepName);

    /**
     * Check if notebook has custom config.
     */
    boolean existsByNotebookId(UUID notebookId);

    /**
     * Find all configs using a specific provider.
     */
    List<PipelineModelConfig> findByProviderNameAndActiveTrue(String providerName);

    /**
     * Find all configs using a specific model.
     */
    List<PipelineModelConfig> findByModelNameAndActiveTrue(String modelName);
}
