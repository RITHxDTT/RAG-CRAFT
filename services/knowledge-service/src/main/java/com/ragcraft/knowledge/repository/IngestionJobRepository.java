package com.ragcraft.knowledge.repository;

import com.ragcraft.knowledge.domain.IngestionJob;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {
    List<IngestionJob> findByDocumentIdOrderByCreatedAtAsc(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
