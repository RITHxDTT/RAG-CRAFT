package com.ragcraft.channel.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mirrors frontend/src/services/channel.service.ts (Channel and ChannelSettings). */
public final class ChannelApi {
    private ChannelApi() {}

    public record ChannelSettings(String title, String welcome, String color, String position, String icon, List<String> domains,
                                  List<String> starters, String password, String expires) {
        public static ChannelSettings defaults(String title, String welcome) {
            return new ChannelSettings(title, welcome, "#0f172a", "BOTTOM_RIGHT", "💬", List.of(), List.of(), "", "");
        }
    }

    public record CreateRequest(@NotBlank String channel, @Size(max = 200) String token) {}

    public record ToggleRequest(Boolean enabled) {}

    public record ConnectRequest(@NotBlank @Size(max = 200) String token) {}

    public record ChannelResponse(UUID id, UUID ownerId, UUID chatbotId, String channel, String publicId, boolean enabled, String status,
                                  String url, String embedCode, String telegramUsername, String tokenMask, ChannelSettings settings,
                                  long messagesThisWeek, Instant createdAt, Instant updatedAt) {}

    /** What a guest sees before chatting. Never includes the password itself. */
    public record PublicBot(String name, String description, List<String> starterQuestions, String welcome, String color, String avatar,
                            boolean passwordRequired, String icon, String position) {}

    public record VerifyRequest(@NotBlank String password) {}

    public record GuestAskRequest(@NotBlank @Size(max = 2000) String question, UUID conversationId, String sessionToken, String password) {}

    public record GuestSource(UUID documentId, String documentName, String sheetName, Integer rowNumber, Integer pageNumber, String excerpt, String url) {}

    public record GuestAnswer(String answer, List<GuestSource> sources, UUID conversationId, String sessionToken, UUID userMessageId, UUID messageId) {}

    public record NamedCount(String name, long value) {}

    public record StatsResponse(long totalChannels, long activeChannels, List<NamedCount> byChannel) {}
}
