package com.ragcraft.knowledge.repository;

import com.ragcraft.knowledge.domain.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId, Pageable pageable);
    void deleteByDocumentId(UUID documentId);
}
