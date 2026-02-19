package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.HardwareInfoResponse;
import org.sirohi.smartnotebook.model.HardwareInfo;
import org.sirohi.smartnotebook.repository.HardwareInfoRepository;
import org.sirohi.smartnotebook.repository.LocalModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.List;

/**
 * Service for detecting hardware capabilities.
 * Uses OSHI library for cross-platform hardware detection.
 *
 * Interview Note: Hardware detection is crucial for model recommendations.
 * Running a 70B model on 8GB RAM will cause OOM kills or severe swapping.
 */
@Service
public class HardwareDetectionService {

    private static final Logger log = LoggerFactory.getLogger(HardwareDetectionService.class);

    private final HardwareInfoRepository hardwareInfoRepository;
    private final LocalModelRepository localModelRepository;

    public HardwareDetectionService(HardwareInfoRepository hardwareInfoRepository,
                                    LocalModelRepository localModelRepository) {
        this.hardwareInfoRepository = hardwareInfoRepository;
        this.localModelRepository = localModelRepository;
    }

    /**
     * Detect current hardware and cache results.
     */
    @Transactional
    public HardwareInfo detectAndCache() {
        log.info("Detecting hardware capabilities...");

        try {
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hal = si.getHardware();

            HardwareInfo info = hardwareInfoRepository.getSingleton()
                    .orElse(new HardwareInfo());

            // Memory
            GlobalMemory memory = hal.getMemory();
            info.setTotalRamGb((int) (memory.getTotal() / (1024L * 1024L * 1024L)));
            info.setAvailableRamGb((int) (memory.getAvailable() / (1024L * 1024L * 1024L)));

            // CPU
            CentralProcessor processor = hal.getProcessor();
            info.setCpuCores(processor.getLogicalProcessorCount());
            info.setCpuModel(processor.getProcessorIdentifier().getName());

            // GPU (find the one with most VRAM)
            List<GraphicsCard> gpus = hal.getGraphicsCards();
            if (!gpus.isEmpty()) {
                GraphicsCard bestGpu = gpus.stream()
                        .max((a, b) -> Long.compare(a.getVRam(), b.getVRam()))
                        .orElse(gpus.get(0));
                info.setGpuName(bestGpu.getName());
                long vramBytes = bestGpu.getVRam();
                if (vramBytes > 0) {
                    info.setGpuVramGb((int) (vramBytes / (1024L * 1024L * 1024L)));
                }
            }

            // OS
            info.setOsName(System.getProperty("os.name"));
            info.setOsVersion(System.getProperty("os.version"));

            // Save
            info = hardwareInfoRepository.save(info);

            log.info("Hardware detected: RAM={}GB ({}GB available), CPU={} cores, GPU={}",
                    info.getTotalRamGb(),
                    info.getAvailableRamGb(),
                    info.getCpuCores(),
                    info.getGpuName());

            // Update model recommendations based on available RAM
            updateModelRecommendations(info.getAvailableRamGb());

            return info;

        } catch (Exception e) {
            log.error("Failed to detect hardware", e);
            throw new RuntimeException("Hardware detection failed", e);
        }
    }

    /**
     * Get cached hardware info, or detect if stale/missing.
     */
    @Transactional
    public HardwareInfo getHardwareInfo() {
        return hardwareInfoRepository.getSingleton()
                .filter(info -> !info.isStale())
                .orElseGet(this::detectAndCache);
    }

    /**
     * Get hardware info as DTO.
     */
    @Transactional
    public HardwareInfoResponse getHardwareInfoResponse() {
        HardwareInfo info = getHardwareInfo();
        return HardwareInfoResponse.from(info);
    }

    /**
     * Update model recommendations based on available RAM.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateModelRecommendations(int availableRamGb) {
        log.debug("Updating model recommendations for {}GB available RAM", availableRamGb);
        localModelRepository.updateRecommendations(availableRamGb);
    }

    /**
     * Get the recommended tier based on hardware.
     */
    public String getRecommendedTier() {
        return hardwareInfoRepository.getRecommendedTier()
                .orElseGet(() -> {
                    detectAndCache();
                    return hardwareInfoRepository.getRecommendedTier().orElse("unknown");
                });
    }

    /**
     * Check if a specific model can run on current hardware.
     */
    public boolean canRunModel(int requiredRamGb) {
        Integer availableRam = hardwareInfoRepository.getAvailableRam().orElse(null);
        if (availableRam == null) {
            HardwareInfo info = detectAndCache();
            availableRam = info.getAvailableRamGb();
        }
        return availableRam != null && availableRam >= requiredRamGb;
    }

    /**
     * Force refresh hardware detection.
     */
    @Transactional
    public HardwareInfo refresh() {
        return detectAndCache();
    }
}
