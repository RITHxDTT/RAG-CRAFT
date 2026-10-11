package com.ragcraft.identity.repository;

import com.ragcraft.identity.domain.QuotaDefaults;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotaDefaultsRepository extends JpaRepository<QuotaDefaults, UUID> {
    Optional<QuotaDefaults> findFirstByOrderByCreatedAtAsc();
}
