package com.ragcraft.catalog.repository;

import com.ragcraft.catalog.domain.PlatformModel;
import com.ragcraft.catalog.domain.PromptTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelRepository extends JpaRepository<PlatformModel, UUID> {
    List<PlatformModel> findAllByOrderByCreatedAtAsc();
    List<PlatformModel> findByEnabledTrueOrderByCreatedAtAsc();
    Optional<PlatformModel> findFirstByIsDefaultTrue();
    List<PlatformModel> findByIsDefaultTrue();
}
