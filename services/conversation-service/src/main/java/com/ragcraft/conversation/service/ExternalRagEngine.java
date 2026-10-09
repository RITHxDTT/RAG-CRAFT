package com.ragcraft.conversation.service;

import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.web.AppException;
import com.ragcraft.conversation.RagProperties;
import com.ragcraft.conversation.api.ConversationApi.SourceResponse;
import com.ragcraft.conversation.client.KnowledgeClient.ReadyDocument;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Delegates to the FastAPI RAG service (retrieval over Qdrant plus Ollama generation).
 * Enabled with rag.mode=external. The request carries the chatbot settings so the RAG service stays stateless
 * with respect to chatbot configuration.
 */
@Component
@ConditionalOnProperty(prefix = "rag", name = "mode", havingValue = "external")
public class ExternalRagEngine implements AnswerEngine {

    private final RestClient client;

    public ExternalRagEngine(RagProperties properties, RestClient.Builder builder) {
        this.client = builder.baseUrl(properties.getUrl()).build();
    }

    @Override
    public Generated answer(ChatbotSummary bot, String question, List<ReadyDocument> documents, String modelOverride) {
        Generated generated = client.post().uri("/internal/answer")
                .body(Map.of("chatbot", bot, "question", question, "model", modelOverride == null ? "" : modelOverride,
                             "document_ids", documents.stream().map(ReadyDocument::id).toList()))
                .retrieve()
                .body(Generated.class);
        if (generated == null) throw new AppException(HttpStatus.BAD_GATEWAY, "The RAG service returned no answer.");
        return new Generated(generated.answer(), generated.sources() == null ? List.<SourceResponse>of() : generated.sources());
    }
}
