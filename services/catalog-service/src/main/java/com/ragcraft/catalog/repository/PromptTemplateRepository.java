package com.ragcraft.catalog.repository;

import com.ragcraft.catalog.domain.PlatformModel;
import com.ragcraft.catalog.domain.PromptTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, UUID> {
    List<PromptTemplate> findAllByOrderByCreatedAtAsc();
    List<PromptTemplate> findByEnabledTrueOrderByCreatedAtAsc();
}
