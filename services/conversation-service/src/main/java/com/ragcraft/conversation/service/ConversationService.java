package com.ragcraft.conversation.service;

import com.ragcraft.common.client.ChatbotAccess;
import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import com.ragcraft.conversation.api.ConversationApi.AnswerResponse;
import com.ragcraft.conversation.api.ConversationApi.ConversationDetailResponse;
import com.ragcraft.conversation.api.ConversationApi.ConversationResponse;
import com.ragcraft.conversation.api.ConversationApi.InternalAskRequest;
import com.ragcraft.conversation.api.ConversationApi.MessageResponse;
import com.ragcraft.conversation.api.ConversationApi.SourceResponse;
import com.ragcraft.conversation.api.ConversationApi.StatsResponse;
import com.ragcraft.conversation.client.AnalyticsClient;
import com.ragcraft.conversation.client.KnowledgeClient;
import com.ragcraft.conversation.domain.Conversation;
import com.ragcraft.conversation.domain.Message;
import com.ragcraft.conversation.domain.MessageSource;
import com.ragcraft.conversation.repository.ConversationRepository;
import com.ragcraft.conversation.repository.MessageRepository;
import com.ragcraft.conversation.repository.MessageSourceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    public static final Set<String> CHANNELS = Set.of("PLAYGROUND", "PUBLIC_LINK", "WEB_WIDGET", "TELEGRAM");

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MessageSourceRepository sources;
    private final ChatbotAccess chatbots;
    private final KnowledgeClient knowledge;
    private final AnalyticsClient analytics;
    private final AnswerEngine engine;

    public ConversationService(ConversationRepository conversations, MessageRepository messages, MessageSourceRepository sources,
                               ChatbotAccess chatbots, KnowledgeClient knowledge, AnalyticsClient analytics, AnswerEngine engine) {
        this.conversations = conversations;
        this.messages = messages;
        this.sources = sources;
        this.chatbots = chatbots;
        this.knowledge = knowledge;
        this.analytics = analytics;
        this.engine = engine;
    }

    /** Playground: the owner (or an admin) asks the chatbot; works for DRAFT chatbots too. */
    @Transactional
    public AnswerResponse ask(UUID chatbotId, String question, UUID conversationId, String model, UserPrincipal principal) {
        ChatbotSummary bot = chatbots.require(chatbotId, principal);
        Conversation conversation = conversationId == null ? null
                : conversations.findByIdAndChatbotId(conversationId, chatbotId)
                        .filter(c -> "PLAYGROUND".equals(c.getChannel()))
                        .orElseThrow(() -> AppException.notFound("Conversation not found. Start a new conversation."));
        return exchange(bot, question, "PLAYGROUND", null, null, conversation, model);
    }

    /** Compare mode: two throw-away playground exchanges with different models. */
    @Transactional
    public List<AnswerResponse> compare(UUID chatbotId, String question, List<String> models, UserPrincipal principal) {
        ChatbotSummary bot = chatbots.require(chatbotId, principal);
        return models.stream().map(model -> exchange(bot, question, "PLAYGROUND", null, null, null, model)).toList();
    }

    /** Guest and Telegram traffic from channel-service. The caller guarantees the chatbot is published. */
    @Transactional
    public AnswerResponse internalAsk(InternalAskRequest request) {
        if (!CHANNELS.contains(request.channel())) throw AppException.badRequest("Unknown channel.");
        ChatbotSummary bot = chatbots.fetch(request.chatbotId());
        Conversation conversation = null;
        if (request.conversationId() != null) {
            conversation = conversations.findByIdAndChatbotIdAndIntegrationIdAndSessionHash(
                    request.conversationId(), request.chatbotId(), request.integrationId(), request.sessionHash()).orElse(null);
        }
        return exchange(bot, request.question(), request.channel(), request.integrationId(), request.sessionHash(), conversation, null);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(UUID chatbotId, UserPrincipal principal) {
        chatbots.require(chatbotId, principal);
        return conversations.findByChatbotIdOrderByUpdatedAtDesc(chatbotId).stream().map(ConversationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetailResponse get(UUID chatbotId, UUID conversationId, UserPrincipal principal) {
        chatbots.require(chatbotId, principal);
        Conversation conversation = conversations.findByIdAndChatbotId(conversationId, chatbotId)
                .orElseThrow(() -> AppException.notFound("Conversation not found."));
        List<Message> rows = messages.findByConversationIdOrderBySequenceAsc(conversationId);
        Map<UUID, List<SourceResponse>> cited = sources.findByMessageIdInOrderByScoreDesc(rows.stream().map(Message::getId).toList()).stream()
                .collect(Collectors.groupingBy(MessageSource::getMessageId, Collectors.mapping(ConversationService::toSource, Collectors.toList())));
        List<MessageResponse> history = rows.stream().map(message -> new MessageResponse(message.getId(), message.getRole(), message.getContent(),
                cited.getOrDefault(message.getId(), List.of()), message.getCreatedAt())).toList();
        return new ConversationDetailResponse(conversation.getId(), conversation.getOwnerId(), conversation.getChatbotId(), conversation.getChannel(),
                conversation.getTitle(), conversation.getCreatedAt(), conversation.getUpdatedAt(), history);
    }

    @Transactional
    public void delete(UUID chatbotId, UUID conversationId, UserPrincipal principal) {
        chatbots.require(chatbotId, principal);
        Conversation conversation = conversations.findByIdAndChatbotId(conversationId, chatbotId)
                .orElseThrow(() -> AppException.notFound("Conversation not found."));
        conversations.delete(conversation);
    }

    @Transactional
    public void deleteForChatbot(UUID chatbotId) {
        conversations.deleteAll(conversations.findByChatbotIdOrderByUpdatedAtDesc(chatbotId));
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(UUID ownerId) {
        Map<String, Long> byChannel = new HashMap<>();
        CHANNELS.forEach(channel -> byChannel.put(channel, 0L));
        conversations.countMessagesByChannel(ownerId).forEach(row -> byChannel.put((String) row[0], (Long) row[1]));
        Map<UUID, Long> byChatbot = new HashMap<>();
        conversations.countMessagesByChatbot(ownerId).forEach(row -> byChatbot.put((UUID) row[0], (Long) row[1]));
        long total = ownerId == null ? conversations.count() : conversations.countByOwnerId(ownerId);
        return new StatsResponse(total, conversations.countMessages(ownerId),
                conversations.countMessagesSince(ownerId, Instant.now().minus(Duration.ofDays(7))), byChannel, byChatbot);
    }

    // ----- core exchange -----

    private AnswerResponse exchange(ChatbotSummary bot, String question, String channel, UUID integrationId, String sessionHash,
                                    Conversation existing, String model) {
        String text = question == null ? "" : question.trim();
        if (text.isEmpty()) throw AppException.badRequest("Enter a question.");

        AnswerEngine.Generated generated = engine.answer(bot, text, knowledge.readyDocuments(bot.id()), model);

        Conversation conversation = existing;
        if (conversation == null) {
            conversation = new Conversation();
            conversation.setChatbotId(bot.id());
            conversation.setOwnerId(bot.ownerId());
            conversation.setChannel(channel);
            conversation.setIntegrationId(integrationId);
            conversation.setSessionHash(sessionHash);
            conversation.setTitle(text.length() > 80 ? text.substring(0, 80) : text);
            conversations.save(conversation);
        } else {
            conversation.touch();
            conversations.save(conversation);
        }
        int sequence = messages.countByConversationId(conversation.getId());
        Message user = save(conversation.getId(), sequence, Message.USER, text);
        Message assistant = save(conversation.getId(), sequence + 1, Message.ASSISTANT, generated.answer());
        List<SourceResponse> cited = new ArrayList<>();
        for (SourceResponse source : generated.sources()) {
            MessageSource row = new MessageSource();
            row.setMessageId(assistant.getId());
            row.setDocumentId(source.documentId());
            row.setChunkId(source.chunkId());
            row.setDocumentName(source.documentName());
            row.setExcerpt(source.excerpt());
            row.setPage(source.pageNumber());
            row.setSheet(source.sheetName());
            row.setRowNumber(source.rowNumber());
            row.setChunkIndex(source.chunkIndex());
            row.setScore(source.score());
            row.setUrl(source.url());
            cited.add(toSource(sources.save(row)));
        }
        analytics.messageExchanged(bot.ownerId(), bot.id(), channel, 2);
        return new AnswerResponse(generated.answer(), cited, conversation.getId(), user.getId(), assistant.getId());
    }

    private Message save(UUID conversationId, int sequence, String role, String content) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSequence(sequence);
        message.setRole(role);
        message.setContent(content);
        return messages.save(message);
    }

    static ConversationResponse toResponse(Conversation c) {
        return new ConversationResponse(c.getId(), c.getOwnerId(), c.getChatbotId(), c.getChannel(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
    }

    static SourceResponse toSource(MessageSource s) {
        return new SourceResponse(s.getChunkId(), s.getDocumentId(), s.getDocumentName(), s.getSheet(), s.getRowNumber(), s.getPage(),
                s.getChunkIndex(), s.getExcerpt(), s.getScore(), s.getUrl());
    }
}
