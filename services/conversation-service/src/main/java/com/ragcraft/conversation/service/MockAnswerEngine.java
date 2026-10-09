package com.ragcraft.conversation.service;

import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.conversation.RagProperties;
import com.ragcraft.conversation.api.ConversationApi.SourceResponse;
import com.ragcraft.conversation.client.KnowledgeClient.Chunk;
import com.ragcraft.conversation.client.KnowledgeClient.ReadyDocument;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulated answers, mirroring frontend/src/services/conversation.service.ts:
 * picks the READY chunks that share the most words with the question, quotes them as citations,
 * and shapes the reply with the chatbot's tone, answer length, and fallback message.
 * No LLM and no vector search are involved.
 */
@Component
@ConditionalOnProperty(prefix = "rag", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockAnswerEngine implements AnswerEngine {

    private final RagProperties properties;

    public MockAnswerEngine(RagProperties properties) {
        this.properties = properties;
    }

    @Override
    public Generated answer(ChatbotSummary bot, String question, List<ReadyDocument> documents, String modelOverride) {
        ChatbotSummary.Settings settings = bot.settings();
        boolean cite = settings == null || !Boolean.FALSE.equals(settings.showCitations());
        String model = modelOverride != null && !modelOverride.isBlank() ? modelOverride : settings == null ? "demo" : settings.modelName();

        if (documents.isEmpty()) {
            String fallback = settings == null || settings.fallbackMessage() == null ? "Add a ready knowledge source to explore simulated answers." : settings.fallbackMessage();
            return new Generated(fallback + "\n\nDemo response · " + model, List.of());
        }

        List<SourceResponse> sources = rank(question, documents).stream().limit(Math.max(1, properties.getMaxSources())).toList();
        StringBuilder answer = new StringBuilder();
        if (settings != null && "FRIENDLY".equals(settings.tone())) answer.append("Happy to help! ");
        answer.append(topic(question));
        if (cite && !sources.isEmpty()) {
            answer.append(" [1]");
            String excerpt = sources.get(0).excerpt();
            answer.append("\n\nFrom ").append(sources.get(0).documentName()).append(": \"")
                  .append(excerpt.length() > 220 ? excerpt.substring(0, 220).trim() + "…" : excerpt).append("\"");
        }
        if (settings != null && "LONG".equals(settings.answerLength())) {
            answer.append("\n\nThis example illustrates the response format; no retrieval or AI generation takes place in the mock engine.");
        }
        answer.append("\n\nDemo response · ").append(model);
        return new Generated(answer.toString(), cite ? sources : List.of());
    }

    private static String topic(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        if (q.contains("leave")) return "Employees receive 18 days of annual leave in this example policy.";
        if (q.contains("refund")) return "This example refund policy allows returns within 30 days with proof of purchase.";
        if (q.contains("start")) return "Start by creating a chatbot, adding knowledge sources, testing in the Playground, and enabling a channel.";
        return "This is a simulated answer to “" + question.trim() + "” based on the chatbot's ready knowledge sources.";
    }

    /** Scores every sample chunk by word overlap with the question, so citations follow the question when possible. */
    static List<SourceResponse> rank(String question, List<ReadyDocument> documents) {
        Set<String> words = Arrays.stream(question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() > 3).collect(Collectors.toSet());
        record Scored(SourceResponse source, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (ReadyDocument document : documents) {
            List<Chunk> chunks = document.chunks() == null || document.chunks().isEmpty()
                    ? List.of(new Chunk(null, document.id(), 0, null, null, null, "Illustrative demo excerpt from " + document.name() + "."))
                    : document.chunks();
            for (Chunk chunk : chunks) {
                String text = chunk.content() == null ? "" : chunk.content();
                long overlap = Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")).filter(words::contains).distinct().count();
                double score = Math.min(0.99, 0.55 + overlap * 0.08);
                String excerpt = text.length() > 400 ? text.substring(0, 400).trim() + "…" : text;
                scored.add(new Scored(new SourceResponse(chunk.id(), document.id(), document.name(), chunk.sheet(), chunk.rowNumber(),
                        chunk.page(), chunk.chunkIndex(), excerpt, score, document.url()), score));
            }
        }
        return scored.stream().sorted((a, b) -> Double.compare(b.score(), a.score())).map(Scored::source).toList();
    }
}
