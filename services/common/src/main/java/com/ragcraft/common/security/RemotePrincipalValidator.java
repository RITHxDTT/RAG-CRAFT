package com.ragcraft.common.security;

import com.ragcraft.common.client.ServiceClient;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * Only identity-service owns the account table, so only it can tell that a token was revoked (logout, suspension,
 * password reset). Every other service asks it, with a short (5 second) cache, so a suspended user cannot keep using an
 * unexpired token. Enabled with REMOTE_TOKEN_VALIDATION=true and never inside identity-service itself.
 * Fails closed: if identity-service cannot be reached the token is rejected.
 */
@Component
@ConditionalOnExpression("'${ragcraft.remote-token-validation:false}' == 'true' && '${spring.application.name:}' != 'identity-service'")
public class RemotePrincipalValidator implements PrincipalValidator {

    private static final Logger log = LoggerFactory.getLogger(RemotePrincipalValidator.class);
    /** How long a verdict is reused: the longest a suspended or signed-out token can keep working in this service. */
    private static final long TTL_MILLIS = 5_000;
    private record Entry(boolean valid, long expiresAt) {}

    private final ServiceClient client;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public RemotePrincipalValidator(ServiceClient client) {
        this.client = client;
    }

    @Override
    public boolean isValid(UserPrincipal principal) {
        String key = principal.userId() + ":" + principal.tokenVersion();
        Entry cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt() > now) return cached.valid();
        try {
            Map<String, Boolean> result = client.asInternal().get()
                    .uri(client.urls().getIdentity() + "/api/internal/users/{id}/session-valid?version={version}", principal.userId(), principal.tokenVersion())
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Boolean>>() {});
            boolean valid = result != null && Boolean.TRUE.equals(result.get("valid"));
            if (cache.size() > 5000) cache.clear();
            cache.put(key, new Entry(valid, now + TTL_MILLIS));
            return valid;
        } catch (RuntimeException ex) {
            log.warn("Could not validate token with identity-service: {}", ex.getMessage());
            return false;
        }
    }
}
