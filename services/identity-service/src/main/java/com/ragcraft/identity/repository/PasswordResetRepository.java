package com.ragcraft.identity.repository;

import com.ragcraft.identity.domain.Organization;
import com.ragcraft.identity.domain.OrganizationMember;
import com.ragcraft.identity.domain.PasswordReset;
import com.ragcraft.identity.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {
    Optional<PasswordReset> findByTokenHash(String tokenHash);
}
