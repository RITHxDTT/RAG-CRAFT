package com.ragcraft.chatbot.repository;

import com.ragcraft.chatbot.domain.ChatbotStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotStatusHistoryRepository extends JpaRepository<ChatbotStatusHistory, UUID> {
    List<ChatbotStatusHistory> findByChatbotIdOrderByCreatedAtDesc(UUID chatbotId);
}
