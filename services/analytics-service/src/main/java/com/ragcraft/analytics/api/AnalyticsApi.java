package com.ragcraft.analytics.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mirrors frontend/src/types/analytics.ts (AnalyticsOverview) and the admin Usage type. */
public final class AnalyticsApi {
    private AnalyticsApi() {}

    public record EventRequest(UUID ownerId, UUID chatbotId, String channel, Integer messages, Instant createdAt) {}

    public record ChartPoint(String name, long value) {}

    public record DailyPoint(String date, String label, long messages, long playground, long published) {}

    public record Usage(long totalChatbots, long activeChatbots, long totalDocuments, long totalKnowledge, long totalConversations,
                        long totalMessages, long messagesThisWeek, long totalChannels, long activeChannels, Long totalUsers, Long activeUsers,
                        Map<String, Long> messagesByChannel, Map<String, Long> messagesByChatbot) {}

    public record Overview(Usage usage, List<DailyPoint> daily, List<ChartPoint> channels, List<ChartPoint> channelStatus,
                           List<ChartPoint> chatbots, List<ChartPoint> botStatus, List<ChartPoint> sourceTypes, List<ChartPoint> sourceStatus) {}
}
