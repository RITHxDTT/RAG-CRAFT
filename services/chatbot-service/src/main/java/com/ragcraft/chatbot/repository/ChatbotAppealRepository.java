package com.ragcraft.chatbot.repository;

import com.ragcraft.chatbot.domain.ChatbotAppeal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotAppealRepository extends JpaRepository<ChatbotAppeal, UUID> {
    Optional<ChatbotAppeal> findFirstByChatbotIdAndStatus(UUID chatbotId, String status);
    List<ChatbotAppeal> findByChatbotIdOrderByCreatedAtDesc(UUID chatbotId);
    List<ChatbotAppeal> findAllByOrderByCreatedAtDesc();
    List<ChatbotAppeal> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}
