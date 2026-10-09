package com.ragcraft.conversation.service;

import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.conversation.api.ConversationApi.SourceResponse;
import com.ragcraft.conversation.client.KnowledgeClient.ReadyDocument;
import java.util.List;

/**
 * Produces an answer plus citations for a question. Two implementations:
 * {@link MockAnswerEngine} (default) and {@link ExternalRagEngine} for the FastAPI RAG service.
 */
public interface AnswerEngine {

    record Generated(String answer, List<SourceResponse> sources) {}

    Generated answer(ChatbotSummary bot, String question, List<ReadyDocument> documents, String modelOverride);
}
