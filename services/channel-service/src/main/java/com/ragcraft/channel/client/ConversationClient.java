package com.ragcraft.channel.client;

import com.ragcraft.common.client.ServiceClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Sends guest and Telegram questions to conversation-service, which owns the chat history. */
@Component
public class ConversationClient {

    public record Source(UUID chunkId, UUID documentId, String documentName, String sheetName, Integer rowNumber, Integer pageNumber,
                         Integer chunkIndex, String excerpt, double score, String url) {}
    public record Answer(String answer, List<Source> sources, UUID conversationId, UUID userMessageId, UUID messageId) {}

    private final ServiceClient client;

    public ConversationClient(ServiceClient client) {
        this.client = client;
    }

    public Answer ask(UUID chatbotId, String question, String channel, UUID integrationId, String sessionHash, UUID conversationId) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("chatbot_id", chatbotId);
        body.put("question", question);
        body.put("channel", channel);
        body.put("integration_id", integrationId);
        body.put("session_hash", sessionHash);
        if (conversationId != null) body.put("conversation_id", conversationId);
        return client.asInternal().post().uri(client.urls().getConversation() + "/api/internal/ask").body(body).retrieve().body(Answer.class);
    }
}
