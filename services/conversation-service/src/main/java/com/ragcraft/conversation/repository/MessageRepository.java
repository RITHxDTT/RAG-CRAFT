package com.ragcraft.conversation.repository;

import com.ragcraft.conversation.domain.Message;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByConversationIdOrderBySequenceAsc(UUID conversationId);
    int countByConversationId(UUID conversationId);
}
