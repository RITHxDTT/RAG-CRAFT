package com.ragcraft.chatbot.repository;

import com.ragcraft.chatbot.domain.Chatbot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatbotRepository extends JpaRepository<Chatbot, UUID> {
    List<Chatbot> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);
    List<Chatbot> findAllByOrderByUpdatedAtDesc(Pageable pageable);
    List<Chatbot> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId, Pageable pageable);
    long countByOwnerId(UUID ownerId);
    long countByOwnerIdAndStatus(UUID ownerId, String status);
    long countByStatus(String status);

    @Query("select c.ownerId, count(c) from Chatbot c where c.ownerId in :ownerIds group by c.ownerId")
    List<Object[]> countByOwners(List<UUID> ownerIds);
}
