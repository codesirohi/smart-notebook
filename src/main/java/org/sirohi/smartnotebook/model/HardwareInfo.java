package org.sirohi.smartnotebook.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for cached hardware information.
 * Used for model recommendations based on available system resources.
 * This is a singleton table (only one row).
 */
@Entity
@Table(name = "hardware_info")
public class HardwareInfo {

    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id = SINGLETON_ID;

    @Column(name = "total_ram_gb")
    private Integer totalRamGb;

    @Column(name = "available_ram_gb")
    private Integer availableRamGb;

    @Column(name = "cpu_cores")
    private Integer cpuCores;

    @Column(name = "cpu_model", length = 200)
    private String cpuModel;

    @Column(name = "gpu_name", length = 200)
    private String gpuName;

    @Column(name = "gpu_vram_gb")
    private Integer gpuVramGb;

    @Column(name = "os_name", length = 100)
    private String osName;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "recommended_tier", length = 20)
    private String recommendedTier;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        id = SINGLETON_ID;  // Ensure singleton
        lastCheckedAt = OffsetDateTime.now();
        calculateRecommendedTier();
    }

    /**
     * Calculate the recommended tier based on hardware.
     */
    public void calculateRecommendedTier() {
        if (totalRamGb == null) {
            recommendedTier = "unknown";
            return;
        }
        if (totalRamGb < 8) {
            recommendedTier = "minimal";
        } else if (totalRamGb < 16) {
            recommendedTier = "standard";
        } else if (totalRamGb < 32) {
            recommendedTier = "performance";
        } else {
            recommendedTier = "high_end";
        }
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = SINGLETON_ID;  // Always use singleton ID
    }

    public Integer getTotalRamGb() {
        return totalRamGb;
    }

    public void setTotalRamGb(Integer totalRamGb) {
        this.totalRamGb = totalRamGb;
    }

    public Integer getAvailableRamGb() {
        return availableRamGb;
    }

    public void setAvailableRamGb(Integer availableRamGb) {
        this.availableRamGb = availableRamGb;
    }

    public Integer getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(Integer cpuCores) {
        this.cpuCores = cpuCores;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public String getGpuName() {
        return gpuName;
    }

    public void setGpuName(String gpuName) {
        this.gpuName = gpuName;
    }

    public Integer getGpuVramGb() {
        return gpuVramGb;
    }

    public void setGpuVramGb(Integer gpuVramGb) {
        this.gpuVramGb = gpuVramGb;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getRecommendedTier() {
        return recommendedTier;
    }

    public void setRecommendedTier(String recommendedTier) {
        this.recommendedTier = recommendedTier;
    }

    public OffsetDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(OffsetDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    /**
     * Check if hardware info is stale (older than 1 hour).
     */
    public boolean isStale() {
        if (lastCheckedAt == null) return true;
        return lastCheckedAt.isBefore(OffsetDateTime.now().minusHours(1));
    }

    /**
     * Check if GPU is available.
     */
    public boolean hasGpu() {
        return gpuName != null && !gpuName.isBlank();
    }

    /**
     * Check if system can run models up to given RAM requirement.
     */
    public boolean canRunModel(int requiredRamGb) {
        if (availableRamGb == null) return false;
        return availableRamGb >= requiredRamGb;
    }

    // Tier constants
    public static final String TIER_MINIMAL = "minimal";      // < 8GB RAM
    public static final String TIER_STANDARD = "standard";    // 8-16GB RAM
    public static final String TIER_PERFORMANCE = "performance"; // 16-32GB RAM
    public static final String TIER_HIGH_END = "high_end";    // 32GB+ RAM
}
