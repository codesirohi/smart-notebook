package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.model.HardwareInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HardwareInfoRepository extends JpaRepository<HardwareInfo, UUID> {

    /**
     * Get the singleton hardware info record.
     * This table only has one row with a fixed ID.
     */
    default Optional<HardwareInfo> getSingleton() {
        return findById(HardwareInfo.SINGLETON_ID);
    }

    /**
     * Get the recommended tier from cached hardware info.
     */
    default Optional<String> getRecommendedTier() {
        return getSingleton().map(HardwareInfo::getRecommendedTier);
    }

    /**
     * Get available RAM from cached hardware info.
     */
    default Optional<Integer> getAvailableRam() {
        return getSingleton().map(HardwareInfo::getAvailableRamGb);
    }
}
