package com.ragcraft.conversation.client;

import com.ragcraft.common.client.ServiceClient;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

/** Reads READY documents (with sample chunks) from knowledge-service to ground answers and build citations. */
@Component
public class KnowledgeClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeClient.class);

    public record Chunk(UUID id, UUID documentId, int chunkIndex, Integer page, String sheet, Integer rowNumber, String content) {}
    public record ReadyDocument(UUID id, String name, String fileType, String url, int chunkCount, List<Chunk> chunks) {}

    private final ServiceClient client;

    public KnowledgeClient(ServiceClient client) {
        this.client = client;
    }

    public List<ReadyDocument> readyDocuments(UUID chatbotId) {
        try {
            List<ReadyDocument> docs = client.asInternal().get()
                    .uri(client.urls().getKnowledge() + "/api/internal/chatbots/{id}/documents?chunkLimit=3", chatbotId)
                    .retrieve().body(new ParameterizedTypeReference<List<ReadyDocument>>() {});
            return docs == null ? List.of() : docs;
        } catch (RuntimeException ex) {
            log.warn("knowledge-service unavailable, answering without sources: {}", ex.getMessage());
            return List.of();
        }
    }
}
