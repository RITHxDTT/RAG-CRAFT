package com.ragcraft.identity.repository;

import com.ragcraft.identity.domain.OrganizationMember;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {
    Optional<OrganizationMember> findFirstByUserIdOrderByCreatedAtAsc(UUID userId);
}
