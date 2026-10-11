package com.ragcraft.catalog.repository;

import com.ragcraft.catalog.domain.AdvancedSettingLimit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvancedSettingLimitRepository extends JpaRepository<AdvancedSettingLimit, UUID> {
    Optional<AdvancedSettingLimit> findBySettingKey(String settingKey);
}
