package com.ragcraft.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shapes mirror frontend/src/types/playground.ts so the UI can switch to the gateway without mapping. */
public final class ConversationApi {
    private ConversationApi() {}

    public record AskRequest(@NotBlank @Size(max = 2000) String question, UUID conversationId, @Size(max = 120) String model) {}

    public record CompareRequest(@NotBlank @Size(max = 2000) String question, @Size(min = 2, max = 2) List<String> models) {}

    /** Used by channel-service for guest and Telegram traffic (internal token). */
    public record InternalAskRequest(UUID chatbotId, @NotBlank @Size(max = 2000) String question, @NotBlank String channel,
                                     UUID integrationId, String sessionHash, UUID conversationId) {}

    public record SourceResponse(UUID chunkId, UUID documentId, String documentName, String sheetName, Integer rowNumber,
                                 Integer pageNumber, Integer chunkIndex, String excerpt, double score, String url) {}

    public record AnswerResponse(String answer, List<SourceResponse> sources, UUID conversationId, UUID userMessageId, UUID messageId) {}

    public record ConversationResponse(UUID id, UUID ownerId, UUID chatbotId, String channel, String title, Instant createdAt, Instant updatedAt) {}

    public record MessageResponse(UUID id, String role, String content, List<SourceResponse> sources, Instant createdAt) {}

    public record ConversationDetailResponse(UUID id, UUID ownerId, UUID chatbotId, String channel, String title, Instant createdAt,
                                             Instant updatedAt, List<MessageResponse> messages) {}

    public record StatsResponse(long totalConversations, long totalMessages, long messagesThisWeek,
                                Map<String, Long> messagesByChannel, Map<UUID, Long> messagesByChatbot) {}
}
