package com.ragcraft.common.security;

/**
 * Optional hook a service can implement to reject otherwise valid tokens,
 * for example identity-service checks the account is still active and the
 * token version has not been revoked.
 */
public interface PrincipalValidator {
    boolean isValid(UserPrincipal principal);
}
