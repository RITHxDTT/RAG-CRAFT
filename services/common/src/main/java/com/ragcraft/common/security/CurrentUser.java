package com.ragcraft.common.security;

import com.ragcraft.common.web.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Convenience accessor for the authenticated caller inside services. */
@Component
public class CurrentUser {

    public UserPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new AppException(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
    }

    public UserPrincipal requireAdmin() {
        UserPrincipal principal = require();
        if (!principal.isAdmin()) {
            throw new AppException(HttpStatus.FORBIDDEN, "Administrator access required.");
        }
        return principal;
    }

    public boolean isInternalCall() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + JwtAuthFilter.ROLE_INTERNAL));
    }
}
