package com.ragcraft.channel.repository;

import com.ragcraft.channel.domain.TelegramUpdate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramUpdateRepository extends JpaRepository<TelegramUpdate, UUID> {
    boolean existsByIntegrationIdAndUpdateId(UUID integrationId, long updateId);
}
