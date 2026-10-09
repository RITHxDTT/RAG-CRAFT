package com.ragcraft.channel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ragcraft.channel.api.ChannelApi.ChannelSettings;
import com.ragcraft.channel.api.ChannelApi.GuestAnswer;
import com.ragcraft.channel.api.ChannelApi.GuestSource;
import com.ragcraft.channel.api.ChannelApi.PublicBot;
import com.ragcraft.channel.client.ConversationClient;
import com.ragcraft.channel.client.TelegramClient;
import com.ragcraft.channel.domain.ChannelIntegration;
import com.ragcraft.channel.domain.ChannelMessage;
import com.ragcraft.channel.domain.TelegramUpdate;
import com.ragcraft.channel.repository.ChannelIntegrationRepository;
import com.ragcraft.channel.repository.ChannelMessageRepository;
import com.ragcraft.channel.repository.TelegramUpdateRepository;
import com.ragcraft.common.client.ChatbotAccess;
import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.web.AppException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Guest chat (share link and widget) and the Telegram webhook. No account is needed; sessions live in the browser. */
@Service
public class PublicChatService {

    private final ChannelIntegrationRepository channels;
    private final ChannelMessageRepository messages;
    private final TelegramUpdateRepository updates;
    private final ChatbotAccess chatbots;
    private final ConversationClient conversations;
    private final TelegramClient telegram;
    private final TokenCipher cipher;
    private final ChannelService channelService;
    private final GuestRateLimiter limiter;

    public PublicChatService(ChannelIntegrationRepository channels, ChannelMessageRepository messages, TelegramUpdateRepository updates,
                             ChatbotAccess chatbots, ConversationClient conversations, TelegramClient telegram, TokenCipher cipher,
                             ChannelService channelService, GuestRateLimiter limiter) {
        this.channels = channels;
        this.messages = messages;
        this.updates = updates;
        this.chatbots = chatbots;
        this.conversations = conversations;
        this.telegram = telegram;
        this.cipher = cipher;
        this.channelService = channelService;
        this.limiter = limiter;
    }

    record Resolved(ChannelIntegration row, ChatbotSummary bot, ChannelSettings settings) {}

    Resolved resolve(String kind, String publicId) {
        String channel = "share".equals(kind) ? ChannelIntegration.PUBLIC_LINK : "widget".equals(kind) ? ChannelIntegration.WEB_WIDGET : null;
        if (channel == null) throw AppException.notFound("Not found.");
        ChannelIntegration row = channels.findByPublicIdAndChannel(publicId, channel).filter(ChannelIntegration::isEnabled)
                .orElseThrow(() -> AppException.notFound("This link is disabled or no longer valid."));
        ChatbotSummary bot;
        try {
            bot = chatbots.fetch(row.getChatbotId());
        } catch (RuntimeException ex) {
            throw AppException.notFound("This chatbot is unavailable.");
        }
        if (!bot.isActive()) throw new AppException(HttpStatus.FORBIDDEN, "This chatbot has not been published as Active.");
        ChannelSettings settings = channelService.settings(row, bot);
        if (!settings.expires().isEmpty()) {
            Instant expiry = ChannelService.parseExpiry(settings.expires());
            if (expiry != null && expiry.isBefore(Instant.now())) throw new AppException(HttpStatus.GONE, "This public link has expired.");
        }
        return new Resolved(row, bot, settings);
    }

    @Transactional(readOnly = true)
    public PublicBot metadata(String kind, String publicId) {
        Resolved resolved = resolve(kind, publicId);
        ChannelSettings s = resolved.settings();
        return new PublicBot(s.title(), resolved.bot().description(), s.starters(), s.welcome(), s.color(), resolved.bot().avatar(),
                !s.password().isEmpty(), s.icon(), s.position());
    }

    @Transactional(readOnly = true)
    public void verify(String kind, String publicId, String password) {
        Resolved resolved = resolve(kind, publicId);
        if (!resolved.settings().password().isEmpty() && !resolved.settings().password().equals(password)) {
            throw AppException.unauthorized("Incorrect link password.");
        }
    }

    @Transactional
    public GuestAnswer ask(String kind, String publicId, String question, UUID conversationId, String sessionToken, String password, String clientKey) {
        limiter.check(clientKey);
        Resolved resolved = resolve(kind, publicId);
        if (!resolved.settings().password().isEmpty() && !resolved.settings().password().equals(password == null ? "" : password)) {
            throw AppException.unauthorized("Enter the public link password.");
        }
        if (question == null || question.isBlank()) throw AppException.badRequest("Enter a question.");
        String session = sessionToken == null || sessionToken.isBlank() ? UUID.randomUUID().toString() : sessionToken;
        ConversationClient.Answer answer = conversations.ask(resolved.bot().id(), question.trim(), resolved.row().getChannel(),
                resolved.row().getId(), sha256(session), conversationId);
        record(resolved.row());
        // Guests never receive internal chunk ids; only what they need to read the citation.
        List<GuestSource> sources = answer.sources() == null ? List.of() : answer.sources().stream()
                .map(s -> new GuestSource(s.documentId(), s.documentName(), s.sheetName(), s.rowNumber(), s.pageNumber(), s.excerpt(), s.url())).toList();
        return new GuestAnswer(answer.answer(), sources, answer.conversationId(), session, answer.userMessageId(), answer.messageId());
    }

    /** Telegram webhook. Returns quickly with 200 so Telegram does not retry; replies are sent by the bot API. */
    @Transactional
    public void telegramUpdate(String publicId, String secretHeader, JsonNode update) {
        ChannelIntegration row = channels.findByPublicId(publicId).filter(ChannelIntegration::isTelegram)
                .orElseThrow(() -> AppException.notFound("Not found."));
        if (row.getWebhookSecret() == null || !row.getWebhookSecret().equals(secretHeader)) throw AppException.unauthorized("Invalid webhook secret.");
        if (!row.isEnabled() || update == null || !update.hasNonNull("update_id")) return;
        long updateId = update.get("update_id").asLong();
        if (updates.existsByIntegrationIdAndUpdateId(row.getId(), updateId)) return;
        JsonNode message = update.path("message");
        String text = message.path("text").asText("");
        if (text.isBlank() || !"private".equals(message.path("chat").path("type").asText(""))) return;
        long chatId = message.path("chat").path("id").asLong();

        TelegramUpdate record = new TelegramUpdate();
        record.setIntegrationId(row.getId());
        record.setUpdateId(updateId);
        ChatbotSummary bot;
        try {
            bot = chatbots.fetch(row.getChatbotId());
        } catch (RuntimeException ex) {
            return;
        }
        String reply;
        if (!bot.isActive()) {
            reply = "This assistant is not published yet.";
        } else {
            ConversationClient.Answer answer = conversations.ask(bot.id(), text.trim(), ChannelIntegration.TELEGRAM, row.getId(), sha256(String.valueOf(chatId)), null);
            reply = answer.answer();
            record(row);
        }
        record.setAnswer(reply);
        record.setSentParts(telegram.sendMessage(cipher.decrypt(row.getCredential()), chatId, reply));
        updates.save(record);
    }

    private void record(ChannelIntegration row) {
        ChannelMessage message = new ChannelMessage();
        message.setIntegrationId(row.getId());
        messages.save(message);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
