package com.ragcraft.channel.repository;

import com.ragcraft.channel.domain.ChannelIntegration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChannelIntegrationRepository extends JpaRepository<ChannelIntegration, UUID> {
    List<ChannelIntegration> findByChatbotIdOrderByCreatedAtAsc(UUID chatbotId);
    Optional<ChannelIntegration> findByIdAndChatbotId(UUID id, UUID chatbotId);
    Optional<ChannelIntegration> findByPublicIdAndChannel(String publicId, String channel);
    Optional<ChannelIntegration> findByPublicId(String publicId);
    Optional<ChannelIntegration> findByChatbotIdAndChannel(UUID chatbotId, String channel);
    long countByOwnerId(UUID ownerId);
    long countByOwnerIdAndEnabledTrue(UUID ownerId);
    long countByEnabledTrue();

    @Query("select c.chatbotId, count(c) from ChannelIntegration c where c.chatbotId in :chatbotIds group by c.chatbotId")
    List<Object[]> countByChatbots(Collection<UUID> chatbotIds);

    @Query("select c.channel, count(c) from ChannelIntegration c where (:ownerId is null or c.ownerId = :ownerId) and c.enabled = true group by c.channel")
    List<Object[]> countEnabledByChannel(UUID ownerId);
}
