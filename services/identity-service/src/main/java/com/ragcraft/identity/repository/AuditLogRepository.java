package com.ragcraft.identity.repository;

import com.ragcraft.identity.domain.AuditLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read and insert only: the audit log is never updated or deleted. */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("select a from AuditLog a where (:admin = '' or lower(a.adminEmail) like lower(concat('%', :admin, '%'))) "
         + "and (:action = '' or a.action = :action) "
         + "and (:target = '' or lower(a.targetLabel) like lower(concat('%', :target, '%')) or lower(a.targetType) like lower(concat('%', :target, '%'))) "
         + "and a.createdAt >= :from and a.createdAt < :to order by a.createdAt desc")
    List<AuditLog> search(@Param("admin") String admin, @Param("action") String action, @Param("target") String target,
                          @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
