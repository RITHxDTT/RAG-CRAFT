package com.ragcraft.chatbot.client;

import com.ragcraft.common.client.ServiceClient;
import com.ragcraft.common.security.UserPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Quota limits and the admin audit log live in identity-service. */
@Component
public class IdentityClient {

    public record Quota(int maxBots, long maxStorageBytes) {}

    private final ServiceClient client;

    public IdentityClient(ServiceClient client) {
        this.client = client;
    }

    public Quota quota(UUID userId) {
        Quota quota = client.asInternal().get().uri(client.urls().getIdentity() + "/api/internal/users/{id}/quota", userId).retrieve().body(Quota.class);
        return quota == null ? new Quota(5, 5L * 1024 * 1024 * 1024) : quota;
    }

    /** Writes the admin action to the audit log. A failure aborts the caller, because an unrecorded admin action is not allowed. */
    public void audit(UserPrincipal admin, String action, String targetType, UUID targetId, String targetLabel, String reason, Map<String, Object> details) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("admin_id", admin.userId());
        body.put("admin_email", admin.email());
        body.put("action", action);
        body.put("target_type", targetType);
        body.put("target_id", targetId);
        body.put("target_label", targetLabel);
        body.put("reason", reason);
        body.put("details", details);
        body.put("source_service", "chatbot-service");
        client.asInternal().post().uri(client.urls().getIdentity() + "/api/internal/audit").body(body).retrieve().toBodilessEntity();
    }
}
