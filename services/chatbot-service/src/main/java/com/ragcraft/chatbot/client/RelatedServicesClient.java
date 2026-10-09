package com.ragcraft.chatbot.client;

import com.ragcraft.chatbot.api.ChatbotApi.DocumentCounts;
import com.ragcraft.common.client.ServiceClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * Best-effort calls to the services that hold chatbot-scoped data (knowledge, conversation, channel)
 * and to identity for owner details. Failures degrade to empty results so chatbot CRUD keeps working.
 */
@Component
public class RelatedServicesClient {

    private static final Logger log = LoggerFactory.getLogger(RelatedServicesClient.class);
    private static final ParameterizedTypeReference<Map<UUID, DocumentCounts>> COUNTS = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<UUID, Long>> LONGS = new ParameterizedTypeReference<>() {};

    public record OwnerSummary(UUID id, String email, String fullName) {}

    private final ServiceClient client;

    public RelatedServicesClient(ServiceClient client) {
        this.client = client;
    }

    public Map<UUID, DocumentCounts> documentCounts(List<UUID> chatbotIds) {
        if (chatbotIds.isEmpty()) return Map.of();
        try {
            Map<UUID, DocumentCounts> counts = client.asInternal().post()
                    .uri(client.urls().getKnowledge() + "/api/internal/documents/counts").body(chatbotIds).retrieve().body(COUNTS);
            return counts == null ? Map.of() : counts;
        } catch (RuntimeException ex) {
            log.debug("knowledge-service unavailable: {}", ex.getMessage());
            return Map.of();
        }
    }

    public Map<UUID, Long> channelCounts(List<UUID> chatbotIds) {
        if (chatbotIds.isEmpty()) return Map.of();
        try {
            Map<UUID, Long> counts = client.asInternal().post()
                    .uri(client.urls().getChannel() + "/api/internal/channels/counts").body(chatbotIds).retrieve().body(LONGS);
            return counts == null ? Map.of() : counts;
        } catch (RuntimeException ex) {
            log.debug("channel-service unavailable: {}", ex.getMessage());
            return Map.of();
        }
    }

    public Map<UUID, OwnerSummary> owners(List<UUID> ownerIds) {
        if (ownerIds.isEmpty()) return Map.of();
        try {
            List<OwnerSummary> owners = client.asInternal().post()
                    .uri(client.urls().getIdentity() + "/api/internal/users/lookup").body(ownerIds).retrieve()
                    .body(new ParameterizedTypeReference<List<OwnerSummary>>() {});
            return owners == null ? Map.of() : owners.stream().collect(java.util.stream.Collectors.toMap(OwnerSummary::id, o -> o));
        } catch (RuntimeException ex) {
            log.debug("identity-service unavailable: {}", ex.getMessage());
            return Map.of();
        }
    }

    /** Asks each service to remove everything scoped to the chatbot. Each service owns its own cleanup. */
    public void cascadeDelete(UUID chatbotId) {
        for (String base : List.of(client.urls().getKnowledge() + "/api/internal/chatbots/{id}/documents",
                                   client.urls().getConversation() + "/api/internal/chatbots/{id}/conversations",
                                   client.urls().getChannel() + "/api/internal/chatbots/{id}/channels")) {
            try {
                client.asInternal().delete().uri(base, chatbotId).retrieve().toBodilessEntity();
            } catch (RuntimeException ex) {
                log.warn("Cascade delete skipped for {} ({}): {}", chatbotId, base, ex.getMessage());
            }
        }
    }

    public boolean telegramConnected(UUID chatbotId) {
        try {
            Map<String, Boolean> result = client.asInternal().get()
                    .uri(client.urls().getChannel() + "/api/internal/chatbots/{id}/channels/telegram-status", chatbotId).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Boolean>>() {});
            return result != null && Boolean.TRUE.equals(result.get("connected"));
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
