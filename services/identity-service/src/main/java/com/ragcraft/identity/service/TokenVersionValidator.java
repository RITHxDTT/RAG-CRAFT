package com.ragcraft.identity.service;

import com.ragcraft.common.security.PrincipalValidator;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.identity.domain.User;
import com.ragcraft.identity.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * identity-service owns the account table, so it can enforce revocation:
 * the account must still be active and the token version must match (logout, reset, deactivation bump it).
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
                .filter(User::isActive)
                .map(user -> user.getTokenVersion() == principal.tokenVersion())
                .orElse(false);
    }
}
