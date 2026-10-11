package com.ragcraft.catalog.repository;

import com.ragcraft.catalog.domain.PlatformModel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelRepository extends JpaRepository<PlatformModel, UUID> {
    List<PlatformModel> findAllByOrderByCreatedAtAsc();
    List<PlatformModel> findByKindOrderByCreatedAtAsc(String kind);
    List<PlatformModel> findByKindAndEnabledTrueOrderByCreatedAtAsc(String kind);
    Optional<PlatformModel> findFirstByKindAndIsDefaultTrue(String kind);
    List<PlatformModel> findByKindAndIsDefaultTrue(String kind);
}
