package com.ragcraft.chatbot.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatbotApi {
    private ChatbotApi() {}

    public record SettingsRequest(
            UUID modelId,
            UUID promptTemplateId,
            @Size(max = 10000) String systemInstruction,
            @Size(max = 10000) String customInstruction,
            String tone,
            @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
            String answerLength,
            @Min(1) @Max(20) Integer topK,
            @Size(max = 2000) String welcomeMessage,
            @Size(max = 2000) String fallbackMessage,
            Boolean showCitations) {}

    public record ChatbotRequest(
            @Size(min = 1, max = 120) String name,
            @Size(max = 2000) String description,
            @Size(max = 16) String avatar,
            String status,
            @Size(max = 10) List<@Size(max = 300) String> starterQuestions,
            SettingsRequest settings) {}

    public record SettingsResponse(
            UUID modelId, String modelName, UUID promptTemplateId, String systemInstruction, String customInstruction,
            String tone, double temperature, String answerLength, int topK, String welcomeMessage, String fallbackMessage,
            boolean showCitations) {}

    public record ChatbotResponse(
            UUID id, UUID organizationId, UUID ownerId, String name, String description, String avatar, String status,
            List<String> starterQuestions, SettingsResponse settings, long documentCount, long readyCount, long failedCount,
            Instant createdAt, Instant updatedAt) {}

    public record DashboardResponse(long totalChatbots, long activeChatbots, long totalDocuments, long readyDocuments,
                                    long failedDocuments, List<ChatbotResponse> recentChatbots) {}

    public record AdminChatbotResponse(UUID id, String name, String status, UUID ownerId, String ownerEmail, String ownerName,
                                       long knowledgeCount, long channelCount, Instant createdAt) {}

    public record StatsResponse(long totalChatbots, long activeChatbots) {}

    /** Per-chatbot document counts served by knowledge-service. */
    public record DocumentCounts(long total, long ready, long failed) {
        public static DocumentCounts zero() { return new DocumentCounts(0, 0, 0); }
    }
}
