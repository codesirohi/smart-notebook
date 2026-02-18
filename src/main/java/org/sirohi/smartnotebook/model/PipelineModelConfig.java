package org.sirohi.smartnotebook.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for pipeline model configuration.
 * Configures which model to use for each pipeline step (extraction, embedding, chat).
 * NULL notebook_id means global default, otherwise notebook-specific override.
 */
@Entity
@Table(name = "pipeline_model_config",
       uniqueConstraints = @UniqueConstraint(columnNames = {"notebook_id", "step_name"}))
public class PipelineModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "notebook_id")
    private UUID notebookId;  // NULL for global default

    @Column(name = "step_name", nullable = false, length = 30)
    private String stepName;  // 'extraction', 'embedding', 'chat'

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "priority")
    private int priority = 0;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getNotebookId() {
        return notebookId;
    }

    public void setNotebookId(UUID notebookId) {
        this.notebookId = notebookId;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
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
     * Check if this is a global config (no notebook scope).
     */
    public boolean isGlobal() {
        return notebookId == null;
    }

    /**
     * Get a parameter value with type casting.
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, T defaultValue) {
        if (parameters == null || !parameters.containsKey(key)) {
            return defaultValue;
        }
        return (T) parameters.get(key);
    }
}
