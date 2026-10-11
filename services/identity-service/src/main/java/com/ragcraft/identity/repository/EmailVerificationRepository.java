package com.ragcraft.identity.repository;

import com.ragcraft.identity.domain.EmailVerification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {
    Optional<EmailVerification> findByTokenHash(String tokenHash);
    List<EmailVerification> findByUserIdAndKindAndUsedAtIsNull(UUID userId, String kind);
}
