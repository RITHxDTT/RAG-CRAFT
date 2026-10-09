package com.ragcraft.knowledge.repository;

import com.ragcraft.knowledge.domain.KnowledgeSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, UUID> {
    Optional<KnowledgeSource> findByChatbotIdAndSourceTypeAndSourceKey(UUID chatbotId, String sourceType, String sourceKey);
}
