package com.ragcraft.identity.service;

import com.ragcraft.common.security.PrincipalValidator;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.identity.domain.User;
import com.ragcraft.identity.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * identity-service owns the account table, so it can enforce revocation: the token version must match
 * (logout, reset, suspension and forced logout bump it). Accounts waiting for deletion stay valid here so
 * they can reach /api/auth/me and /api/auth/restore; the other services reject them through session-valid.
 */
@Component
public class TokenVersionValidator implements PrincipalValidator {

    private final UserRepository users;

    public TokenVersionValidator(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean isValid(UserPrincipal principal) {
        return users.findById(principal.userId())
                .filter(user -> User.ACTIVE.equals(user.getStatus()) || User.PENDING_DELETION.equals(user.getStatus()))
                .map(user -> user.getTokenVersion() == principal.tokenVersion())
                .orElse(false);
    }
}
