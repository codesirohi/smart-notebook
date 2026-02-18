package org.sirohi.smartnotebook.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for local Ollama models.
 * Tracks available and installed models with hardware requirements.
 */
@Entity
@Table(name = "local_models")
public class LocalModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "model_name", nullable = false, unique = true, length = 100)
    private String modelName;

    @Column(name = "model_type", nullable = false, length = 20)
    private String modelType;  // 'llm' or 'embedding'

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "parameter_count", length = 20)
    private String parameterCount;  // '7B', '13B', '70B'

    @Column(name = "quantization", length = 20)
    private String quantization;  // 'Q4_0', 'Q8_0', 'F16'

    @Column(name = "context_length")
    private Integer contextLength;

    @Column(name = "min_ram_gb", nullable = false)
    private int minRamGb = 4;

    @Column(name = "recommended_ram_gb")
    private Integer recommendedRamGb;

    @Column(name = "is_installed")
    private boolean installed = false;

    @Column(name = "is_recommended")
    private boolean recommended = false;

    @Column(name = "install_status", length = 20)
    private String installStatus = "not_installed";

    @Column(name = "install_progress")
    private int installProgress = 0;

    @Column(name = "installed_at")
    private OffsetDateTime installedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getParameterCount() {
        return parameterCount;
    }

    public void setParameterCount(String parameterCount) {
        this.parameterCount = parameterCount;
    }

    public String getQuantization() {
        return quantization;
    }

    public void setQuantization(String quantization) {
        this.quantization = quantization;
    }

    public Integer getContextLength() {
        return contextLength;
    }

    public void setContextLength(Integer contextLength) {
        this.contextLength = contextLength;
    }

    public int getMinRamGb() {
        return minRamGb;
    }

    public void setMinRamGb(int minRamGb) {
        this.minRamGb = minRamGb;
    }

    public Integer getRecommendedRamGb() {
        return recommendedRamGb;
    }

    public void setRecommendedRamGb(Integer recommendedRamGb) {
        this.recommendedRamGb = recommendedRamGb;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
    }

    public String getInstallStatus() {
        return installStatus;
    }

    public void setInstallStatus(String installStatus) {
        this.installStatus = installStatus;
    }

    public int getInstallProgress() {
        return installProgress;
    }

    public void setInstallProgress(int installProgress) {
        this.installProgress = installProgress;
    }

    public OffsetDateTime getInstalledAt() {
        return installedAt;
    }

    public void setInstalledAt(OffsetDateTime installedAt) {
        this.installedAt = installedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Check if this model can run on the given RAM.
     */
    public boolean canRunOn(int availableRamGb) {
        return availableRamGb >= minRamGb;
    }

    /**
     * Get human-readable size string.
     */
    public String getHumanReadableSize() {
        if (sizeBytes == null) return "Unknown";
        if (sizeBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
        }
        return String.format("%.1f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0));
    }
}
