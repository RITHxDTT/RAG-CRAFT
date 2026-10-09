package com.ragcraft.channel.repository;

import com.ragcraft.channel.domain.ChannelMessage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, UUID> {
    @Query("select m.integrationId, count(m) from ChannelMessage m where m.integrationId in :ids and m.createdAt >= :since group by m.integrationId")
    List<Object[]> countSince(Collection<UUID> ids, Instant since);
}
