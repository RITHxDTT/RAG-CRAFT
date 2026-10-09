package com.ragcraft.analytics.service;

import com.ragcraft.analytics.api.AnalyticsApi.ChartPoint;
import com.ragcraft.analytics.api.AnalyticsApi.DailyPoint;
import com.ragcraft.analytics.api.AnalyticsApi.EventRequest;
import com.ragcraft.analytics.api.AnalyticsApi.Overview;
import com.ragcraft.analytics.api.AnalyticsApi.Usage;
import com.ragcraft.analytics.client.StatsClient;
import com.ragcraft.analytics.domain.AnalyticsEvent;
import com.ragcraft.analytics.repository.AnalyticsEventRepository;
import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private static final Set<String> CHANNELS = Set.of("PLAYGROUND", "PUBLIC_LINK", "WEB_WIDGET", "TELEGRAM");

    private final AnalyticsEventRepository events;
    private final StatsClient stats;

    public AnalyticsService(AnalyticsEventRepository events, StatsClient stats) {
        this.events = events;
        this.stats = stats;
    }

    @Transactional
    public void record(EventRequest request) {
        if (request.ownerId() == null || request.chatbotId() == null || request.channel() == null || !CHANNELS.contains(request.channel())) {
            throw AppException.badRequest("Event needs owner_id, chatbot_id and a known channel.");
        }
        AnalyticsEvent event = new AnalyticsEvent();
        event.setOwnerId(request.ownerId());
        event.setChatbotId(request.chatbotId());
        event.setChannel(request.channel());
        event.setMessages(request.messages() == null ? 2 : Math.max(1, request.messages()));
        event.setOccurredAt(request.createdAt() == null ? Instant.now() : request.createdAt());
        events.save(event);
    }

    /** User overview: scoped to the caller. Admin overview: platform wide, plus user counts. */
    @Transactional(readOnly = true)
    public Overview overview(UserPrincipal principal, boolean platform, int days, String locale) {
        if (platform && !principal.isAdmin()) throw AppException.forbidden("Administrator access required.");
        UUID owner = platform ? null : principal.userId();
        int window = Math.max(1, Math.min(days, 90));

        StatsClient.ChatbotStats bots = stats.chatbots(owner);
        List<ChatbotSummary> botList = stats.chatbotList(owner);
        StatsClient.KnowledgeStats knowledge = stats.knowledge(owner);
        StatsClient.ConversationStats conversations = stats.conversations(owner);
        StatsClient.ChannelStats channels = stats.channels(owner);
        StatsClient.IdentityStats users = platform ? stats.identity() : null;

        Map<UUID, String> names = botList.stream().collect(Collectors.toMap(ChatbotSummary::id, ChatbotSummary::name, (a, b) -> a));
        Map<String, Long> byChatbotName = new LinkedHashMap<>();
        conversations.messagesByChatbot().forEach((id, count) -> byChatbotName.merge(names.getOrDefault(id, "Deleted chatbot"), count, Long::sum));

        Usage usage = new Usage(bots.totalChatbots(), bots.activeChatbots(), knowledge.totalDocuments(), knowledge.totalKnowledge(),
                conversations.totalConversations(), conversations.totalMessages(), conversations.messagesThisWeek(),
                channels.totalChannels(), channels.activeChannels(), users == null ? null : users.totalUsers(), users == null ? null : users.activeUsers(),
                conversations.messagesByChannel(), byChatbotName);

        List<ChartPoint> chatbotPoints = byChatbotName.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(6)
                .map(entry -> new ChartPoint(entry.getKey(), entry.getValue())).toList();
        List<ChartPoint> channelPoints = CHANNELS.stream().sorted()
                .map(channel -> new ChartPoint(channel.replace('_', ' '), conversations.messagesByChannel().getOrDefault(channel, 0L))).toList();
        List<ChartPoint> channelStatus = List.of(new ChartPoint("Live", channels.activeChannels()),
                new ChartPoint("Off", Math.max(0, channels.totalChannels() - channels.activeChannels())));
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        botList.forEach(bot -> statusCounts.merge(bot.status(), 1L, Long::sum));
        List<ChartPoint> botStatus = statusCounts.entrySet().stream().map(e -> new ChartPoint(e.getKey(), e.getValue())).toList();

        return new Overview(usage, daily(owner, window, locale), channelPoints, channelStatus, chatbotPoints, botStatus,
                knowledge.byFileType().stream().map(c -> new ChartPoint(c.name(), c.value())).toList(),
                knowledge.byStatus().stream().map(c -> new ChartPoint(c.name(), c.value())).toList());
    }

    List<DailyPoint> daily(UUID owner, int days, String locale) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate first = today.minusDays(days - 1L);
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (int index = 0; index < days; index++) buckets.put(first.plusDays(index), new long[3]);
        for (AnalyticsEvent event : events.since(owner, first.atStartOfDay(zone).toInstant())) {
            long[] bucket = buckets.get(event.getOccurredAt().atZone(zone).toLocalDate());
            if (bucket == null) continue;
            bucket[0] += event.getMessages();
            if ("PLAYGROUND".equals(event.getChannel())) bucket[1] += event.getMessages();
            else bucket[2] += event.getMessages();
        }
        Locale tag = locale == null || locale.isBlank() ? Locale.ENGLISH : Locale.forLanguageTag(locale);
        DateTimeFormatter label = DateTimeFormatter.ofPattern("MMM d", tag);
        List<DailyPoint> result = new ArrayList<>(days);
        buckets.forEach((date, bucket) -> result.add(new DailyPoint(date.toString(), date.format(label), bucket[0], bucket[1], bucket[2])));
        return result;
    }
}
