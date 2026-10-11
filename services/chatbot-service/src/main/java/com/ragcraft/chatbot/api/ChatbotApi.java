package com.ragcraft.chatbot.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatbotApi {
    private ChatbotApi() {}

    /**
     * Every field is optional on update. Numeric settings are checked against the admin-defined limits in catalog-service,
     * so the annotations here only reject nonsense. The embedding model can be sent on create and never changes after.
     */
    public record SettingsRequest(
            UUID modelId,
            UUID fallbackModelId,
            Boolean clearFallbackModel,
            String embeddingModel,
            UUID promptTemplateId,
            @Size(max = 10000) String systemInstruction,
            @Size(max = 10000) String customInstruction,
            String tone,
            String answerLength,
            String formatting,
            String language,
            Double temperature,
            Integer maxTokens,
            Integer topK,
            String searchMode,
            Integer maxContextTokens,
            Integer chunkSize,
            Integer chunkOverlap,
            Boolean answerFromDocumentsOnly,
            @Size(max = 2000) String welcomeMessage,
            @Size(max = 2000) String fallbackMessage,
            Boolean showCitations) {}

    public record ChatbotRequest(
            @Size(min = 1, max = 50) String name,
            @Size(max = 200) String description,
            String avatar,
            String status,
            @Size(max = 4) List<@Size(max = 300) String> starterQuestions,
            SettingsRequest settings) {}

    public record SettingsResponse(
            UUID modelId, String modelName, UUID fallbackModelId, String fallbackModelName, String embeddingModel, UUID promptTemplateId,
            String systemInstruction, String customInstruction, String tone, String answerLength, String formatting, String language,
            double temperature, int maxTokens, int topK, String searchMode, int maxContextTokens, int chunkSize, int chunkOverlap,
            boolean answerFromDocumentsOnly, String welcomeMessage, String fallbackMessage, boolean showCitations) {}

    public record ChatbotResponse(
            UUID id, UUID organizationId, UUID ownerId, String name, String description, String avatar, String status,
            String disabledReason, Instant disabledAt, List<String> starterQuestions, SettingsResponse settings,
            long documentCount, long readyCount, long failedCount, String kbStatus, long channelCount,
            Instant createdAt, Instant updatedAt) {}

    public record DashboardResponse(long totalChatbots, long activeChatbots, long totalDocuments, long readyDocuments,
                                    long failedDocuments, List<ChatbotResponse> recentChatbots) {}

    public record AdminChatbotResponse(UUID id, String name, String status, String disabledReason, UUID ownerId, String ownerEmail,
                                       String ownerName, long knowledgeCount, long channelCount, Instant createdAt) {}

    public record StatsResponse(long totalChatbots, long activeChatbots) {}

    /** Per-chatbot document counts served by knowledge-service. */
    public record DocumentCounts(long total, long ready, long failed) {
        public static DocumentCounts zero() { return new DocumentCounts(0, 0, 0); }
    }

    /** What must be true before a chatbot can be published. */
    public record PublishChecklist(boolean hasDocument, boolean modelSelected, boolean channelReady, boolean ready) {}

    public record AppealRequest(@NotBlank @Size(max = 500) String message) {}

    public record ReasonRequest(@NotBlank String reason) {}

    public record DecisionRequest(String reason) {}

    public record AppealResponse(UUID id, UUID chatbotId, String chatbotName, UUID ownerId, String ownerEmail, String message, String status,
                                 String decisionReason, Instant createdAt, Instant decidedAt) {}

    public record HistoryResponse(String fromStatus, String toStatus, String actorRole, String reason, Instant createdAt) {}
}
