package com.ragcraft.knowledge.repository;

import com.ragcraft.knowledge.domain.Document;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByChatbotIdOrderByCreatedAtDesc(UUID chatbotId);
    List<Document> findByChatbotIdAndStatusOrderByCreatedAtDesc(UUID chatbotId, String status);
    Optional<Document> findByIdAndChatbotId(UUID id, UUID chatbotId);
    List<Document> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId, Pageable pageable);
    List<Document> findAllByOrderByUpdatedAtDesc(Pageable pageable);
    List<Document> findByStatusNotIn(Collection<String> statuses);
    long countByOwnerId(UUID ownerId);
    long countByOwnerIdAndFileTypeNot(UUID ownerId, String fileType);
    long countByFileTypeNot(String fileType);
    long countByOwnerIdAndStatus(UUID ownerId, String status);
    long countByStatus(String status);

    @Query("select d.chatbotId, d.status, count(d) from Document d where d.chatbotId in :chatbotIds group by d.chatbotId, d.status")
    List<Object[]> countByChatbotAndStatus(Collection<UUID> chatbotIds);

    @Query("select d.fileType, count(d) from Document d where (:ownerId is null or d.ownerId = :ownerId) group by d.fileType")
    List<Object[]> countByFileType(UUID ownerId);

    @Query("select d.status, count(d) from Document d where (:ownerId is null or d.ownerId = :ownerId) group by d.status")
    List<Object[]> countByStatusGrouped(UUID ownerId);
}
