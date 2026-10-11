package com.ragcraft.identity.repository;

import com.ragcraft.identity.domain.UserQuota;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserQuotaRepository extends JpaRepository<UserQuota, UUID> {
    Optional<UserQuota> findByUserId(UUID userId);
}
