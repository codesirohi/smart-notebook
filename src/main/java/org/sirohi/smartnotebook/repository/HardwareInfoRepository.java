package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.model.HardwareInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
    @Query("SELECT h.recommendedTier FROM HardwareInfo h WHERE h.id = '00000000-0000-0000-0000-000000000001'")
    Optional<String> getRecommendedTier();

    /**
     * Get available RAM from cached hardware info.
     */
    @Query("SELECT h.availableRamGb FROM HardwareInfo h WHERE h.id = '00000000-0000-0000-0000-000000000001'")
    Optional<Integer> getAvailableRam();
}
