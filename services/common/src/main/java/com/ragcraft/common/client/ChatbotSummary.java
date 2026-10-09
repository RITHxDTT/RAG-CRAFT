package com.ragcraft.common.client;

import java.util.List;
import java.util.UUID;

/**
 * The slice of a chatbot other services need: ownership plus the settings that shape answers.
 * Served by chatbot-service at GET /api/internal/chatbots/{id}.
 */
public record ChatbotSummary(
        UUID id,
        UUID organizationId,
        UUID ownerId,
        String name,
        String description,
        String avatar,
        String status,
        List<String> starterQuestions,
        Settings settings) {

    public record Settings(
            UUID modelId,
            String modelName,
            UUID promptTemplateId,
            String systemInstruction,
            String customInstruction,
            String tone,
            Double temperature,
            String answerLength,
            Integer topK,
            String welcomeMessage,
            String fallbackMessage,
            Boolean showCitations) {
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
