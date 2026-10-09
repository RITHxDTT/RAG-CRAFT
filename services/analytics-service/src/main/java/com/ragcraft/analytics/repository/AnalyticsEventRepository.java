package com.ragcraft.analytics.repository;

import com.ragcraft.analytics.domain.AnalyticsEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {
    @Query("select e from AnalyticsEvent e where (:ownerId is null or e.ownerId = :ownerId) and e.occurredAt >= :since order by e.occurredAt asc")
    List<AnalyticsEvent> since(UUID ownerId, Instant since);
}
