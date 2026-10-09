package com.ragcraft.conversation.repository;

import com.ragcraft.conversation.domain.Conversation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findByChatbotIdOrderByUpdatedAtDesc(UUID chatbotId);
    Optional<Conversation> findByIdAndChatbotId(UUID id, UUID chatbotId);
    Optional<Conversation> findByIdAndChatbotIdAndIntegrationIdAndSessionHash(UUID id, UUID chatbotId, UUID integrationId, String sessionHash);
    long countByOwnerId(UUID ownerId);

    @Query("select count(m) from Message m join Conversation c on m.conversationId = c.id where (:ownerId is null or c.ownerId = :ownerId)")
    long countMessages(UUID ownerId);

    @Query("select count(m) from Message m join Conversation c on m.conversationId = c.id where (:ownerId is null or c.ownerId = :ownerId) and m.createdAt >= :since")
    long countMessagesSince(UUID ownerId, Instant since);

    @Query("select c.channel, count(m) from Message m join Conversation c on m.conversationId = c.id where (:ownerId is null or c.ownerId = :ownerId) group by c.channel")
    List<Object[]> countMessagesByChannel(UUID ownerId);

    @Query("select c.chatbotId, count(m) from Message m join Conversation c on m.conversationId = c.id where (:ownerId is null or c.ownerId = :ownerId) group by c.chatbotId")
    List<Object[]> countMessagesByChatbot(UUID ownerId);
}
