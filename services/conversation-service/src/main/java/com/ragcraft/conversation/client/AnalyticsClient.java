package com.ragcraft.conversation.client;

import com.ragcraft.common.client.ServiceClient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Publishes a message event to analytics-service after each exchange; failures never affect the answer. */
@Component
public class AnalyticsClient {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsClient.class);

    private final ServiceClient client;

    public AnalyticsClient(ServiceClient client) {
        this.client = client;
    }

    @Async
    public void messageExchanged(UUID ownerId, UUID chatbotId, String channel, int messages) {
        try {
            client.asInternal().post().uri(client.urls().getAnalytics() + "/api/internal/events")
                    .body(Map.of("owner_id", ownerId, "chatbot_id", chatbotId, "channel", channel, "messages", messages,
                                 "created_at", Instant.now().toString()))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            log.debug("analytics-service unavailable: {}", ex.getMessage());
        }
    }
}
