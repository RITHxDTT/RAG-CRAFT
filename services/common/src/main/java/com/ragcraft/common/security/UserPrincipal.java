package com.ragcraft.common.security;

import java.util.UUID;

/** The authenticated caller, decoded from the JWT. Shared by every service. */
public record UserPrincipal(UUID userId, UUID organizationId, String email, String role, int tokenVersion) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    /** True when the caller owns the resource or is a platform admin. */
    public boolean canAccess(UUID ownerId) {
        return isAdmin() || (ownerId != null && ownerId.equals(userId));
    }
}
