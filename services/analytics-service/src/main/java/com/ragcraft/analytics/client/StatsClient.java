package com.ragcraft.analytics.client;

import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.client.ServiceClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/**
 * Pulls counts from each service's /api/internal/stats endpoint. Every call is best effort:
 * a service that is down contributes zeros instead of failing the whole overview.
 */
@Component
public class StatsClient {

    private static final Logger log = LoggerFactory.getLogger(StatsClient.class);

    public record NamedCount(String name, long value) {}
    public record IdentityStats(long totalUsers, long activeUsers) {}
    public record ChatbotStats(long totalChatbots, long activeChatbots) {}
    public record KnowledgeStats(long totalKnowledge, long totalDocuments, long readyDocuments, long failedDocuments,
                                 List<NamedCount> byFileType, List<NamedCount> byStatus) {}
    public record ConversationStats(long totalConversations, long totalMessages, long messagesThisWeek,
                                    Map<String, Long> messagesByChannel, Map<UUID, Long> messagesByChatbot) {}
    public record ChannelStats(long totalChannels, long activeChannels, List<NamedCount> byChannel) {}

    private final ServiceClient client;

    public StatsClient(ServiceClient client) {
        this.client = client;
    }

    public IdentityStats identity() {
        return get(client.urls().getIdentity() + "/api/internal/stats", IdentityStats.class, () -> new IdentityStats(0, 0), null);
    }

    public ChatbotStats chatbots(UUID ownerId) {
        return get(client.urls().getChatbot() + "/api/internal/stats", ChatbotStats.class, () -> new ChatbotStats(0, 0), ownerId);
    }

    public List<ChatbotSummary> chatbotList(UUID ownerId) {
        try {
            String uri = client.urls().getChatbot() + "/api/internal/chatbots" + (ownerId == null ? "" : "?ownerId=" + ownerId);
            List<ChatbotSummary> list = client.asInternal().get().uri(uri).retrieve().body(new ParameterizedTypeReference<List<ChatbotSummary>>() {});
            return list == null ? List.of() : list;
        } catch (RuntimeException ex) {
            log.debug("chatbot-service unavailable: {}", ex.getMessage());
            return List.of();
        }
    }

    public KnowledgeStats knowledge(UUID ownerId) {
        return get(client.urls().getKnowledge() + "/api/internal/stats", KnowledgeStats.class,
                () -> new KnowledgeStats(0, 0, 0, 0, List.of(), List.of()), ownerId);
    }

    public ConversationStats conversations(UUID ownerId) {
        return get(client.urls().getConversation() + "/api/internal/stats", ConversationStats.class,
                () -> new ConversationStats(0, 0, 0, Map.of(), Map.of()), ownerId);
    }

    public ChannelStats channels(UUID ownerId) {
        return get(client.urls().getChannel() + "/api/internal/stats", ChannelStats.class, () -> new ChannelStats(0, 0, List.of()), ownerId);
    }

    private <T> T get(String base, Class<T> type, Supplier<T> fallback, UUID ownerId) {
        try {
            T result = client.asInternal().get().uri(ownerId == null ? base : base + "?ownerId=" + ownerId).retrieve().body(type);
            return result == null ? fallback.get() : result;
        } catch (RuntimeException ex) {
            log.debug("{} unavailable: {}", base, ex.getMessage());
            return fallback.get();
        }
    }
}
