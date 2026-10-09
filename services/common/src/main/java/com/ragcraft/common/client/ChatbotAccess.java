package com.ragcraft.common.client;

import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ownership check used by knowledge, conversation, and channel services:
 * fetch the chatbot from chatbot-service and confirm the caller owns it (or is an admin).
 * Chatbots are referenced across services by id only; there are no cross-database foreign keys.
 */
@Component
public class ChatbotAccess {

    private final ServiceClient client;

    public ChatbotAccess(ServiceClient client) {
        this.client = client;
    }

    public ChatbotSummary fetch(UUID chatbotId) {
        ChatbotSummary bot = client.asInternal().get()
                .uri(client.urls().getChatbot() + "/api/internal/chatbots/{id}", chatbotId)
                .retrieve()
                .body(ChatbotSummary.class);
        if (bot == null) {
            throw AppException.notFound("Chatbot not found.");
        }
        return bot;
    }

    public ChatbotSummary require(UUID chatbotId, UserPrincipal principal) {
        ChatbotSummary bot = fetch(chatbotId);
        if (!principal.canAccess(bot.ownerId())) {
            throw AppException.notFound("Chatbot not found.");
        }
        return bot;
    }
}
