package com.ragcraft.conversation.repository;

import com.ragcraft.conversation.domain.MessageSource;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageSourceRepository extends JpaRepository<MessageSource, UUID> {
    List<MessageSource> findByMessageIdInOrderByScoreDesc(Collection<UUID> messageIds);
}
