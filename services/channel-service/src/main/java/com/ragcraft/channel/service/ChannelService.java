package com.ragcraft.channel.service;

import com.ragcraft.channel.api.ChannelApi.ChannelResponse;
import com.ragcraft.channel.api.ChannelApi.ChannelSettings;
import com.ragcraft.channel.api.ChannelApi.NamedCount;
import com.ragcraft.channel.api.ChannelApi.StatsResponse;
import com.ragcraft.channel.client.TelegramClient;
import com.ragcraft.channel.domain.ChannelIntegration;
import com.ragcraft.channel.repository.ChannelIntegrationRepository;
import com.ragcraft.channel.repository.ChannelMessageRepository;
import com.ragcraft.common.client.ChatbotAccess;
import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.security.RagCraftProperties;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import com.ragcraft.common.web.Json;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-facing channel management (same operations as the FastAPI channels router and the V2 frontend). */
@Service
public class ChannelService {

    private static final Set<String> KINDS = Set.of(ChannelIntegration.PUBLIC_LINK, ChannelIntegration.WEB_WIDGET, ChannelIntegration.TELEGRAM);
    private static final Pattern DOMAIN = Pattern.compile("^[a-z0-9.-]+\\.[a-z]{2,}$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final ChannelIntegrationRepository channels;
    private final ChannelMessageRepository messages;
    private final ChatbotAccess chatbots;
    private final TelegramClient telegram;
    private final TokenCipher cipher;
    private final RagCraftProperties properties;
    private final SecureRandom random = new SecureRandom();

    public ChannelService(ChannelIntegrationRepository channels, ChannelMessageRepository messages, ChatbotAccess chatbots,
                          TelegramClient telegram, TokenCipher cipher, RagCraftProperties properties) {
        this.channels = channels;
        this.messages = messages;
        this.chatbots = chatbots;
        this.telegram = telegram;
        this.cipher = cipher;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<ChannelResponse> list(UUID chatbotId, UserPrincipal principal) {
        ChatbotSummary bot = chatbots.require(chatbotId, principal);
        return respond(channels.findByChatbotIdOrderByCreatedAtAsc(chatbotId), bot);
    }

    @Transactional
    public ChannelResponse create(UUID chatbotId, String kind, String token, UserPrincipal principal) {
        ChatbotSummary bot = chatbots.require(chatbotId, principal);
        String channel = kind == null ? "" : kind.toUpperCase(Locale.ROOT);
        if (!KINDS.contains(channel)) throw AppException.badRequest("Unknown channel.");
        channels.findByChatbotIdAndChannel(chatbotId, channel).ifPresent(existing -> { throw AppException.conflict("This channel already exists."); });

        ChannelIntegration row = new ChannelIntegration();
        row.setChatbotId(chatbotId);
        row.setOwnerId(bot.ownerId());
        row.setChannel(channel);
        row.setPublicId(randomId(16));
        row.setWebhookSecret(randomId(24));
        String welcome = bot.settings() == null || bot.settings().welcomeMessage() == null ? "Hello! How can I help you today?" : bot.settings().welcomeMessage();
        ChannelSettings settings = ChannelSettings.defaults(bot.name(), welcome);
        row.setSettings(Json.write(new ChannelSettings(settings.title(), settings.welcome(), settings.color(), settings.position(), settings.icon(),
                List.of(), bot.starterQuestions() == null ? List.of() : bot.starterQuestions(), "", "")));
        if (ChannelIntegration.TELEGRAM.equals(channel)) {
            connect(row, token);
        } else {
            boolean live = ChannelIntegration.PUBLIC_LINK.equals(channel);
            row.setEnabled(live);
            row.setStatus(live ? ChannelIntegration.LIVE : ChannelIntegration.OFF);
        }
        return respond(List.of(channels.save(row)), bot).get(0);
    }

    @Transactional
    public ChannelResponse saveSettings(UUID chatbotId, UUID id, ChannelSettings incoming, UserPrincipal principal) {
        ChatbotSummary bot = chatbots.require(chatbotId, principal);
        ChannelIntegration row = require(chatbotId, id);
        ChannelSettings current = settings(row, bot);
        List<String> domains = new ArrayList<>();
        for (String domain : incoming.domains() == null ? current.domains() : incoming.domains()) {
            String value = domain == null ? "" : domain.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) continue;
            if (!DOMAIN.matcher(value).matches()) throw AppException.badRequest("Use domain names without paths, such as example.com.");
            if (!domains.contains(value)) domains.add(value);
        }
        String color = incoming.color() == null ? current.color() : incoming.color().trim();
        if (!COLOR.matcher(color).matches()) throw AppException.badRequest("Choose a valid color.");
        String expires = incoming.expires() == null ? current.expires() : incoming.expires().trim();
        if (!expires.isEmpty() && parseExpiry(expires) == null) throw AppException.badRequest("Enter a valid expiration date.");
        String position = incoming.position() == null ? current.position() : incoming.position().toUpperCase(Locale.ROOT);
        if (!Set.of("BOTTOM_RIGHT", "BOTTOM_LEFT").contains(position)) throw AppException.badRequest("Unknown widget position.");
        List<String> starters = (incoming.starters() == null ? current.starters() : incoming.starters()).stream()
                .map(s -> s == null ? "" : s.trim()).filter(s -> !s.isEmpty()).limit(10).toList();
        ChannelSettings merged = new ChannelSettings(
                blankTo(incoming.title(), current.title()), blankTo(incoming.welcome(), current.welcome()), color, position,
                blankTo(incoming.icon(), current.icon()), domains, starters,
                incoming.password() == null ? current.password() : incoming.password(), expires);
        row.setSettings(Json.write(merged));
        if (ChannelIntegration.WEB_WIDGET.equals(row.getChannel()) && !row.isEnabled()) {
            // Saving the widget configuration generates the embed code and turns the widget on, as in the frontend.
            row.setEnabled(true);
            row.setStatus(ChannelIntegration.LIVE);
        }
        return respond(List.of(channels.save(row)), bot).get(0);
    }

    @Transactional
    public ChannelResponse toggle(UUID chatbotId, UUID id, Boolean enabled, UserPrincipal principal) {
        ChatbotSummary bot = chatbots.require(chatbotId, principal);
        ChannelIntegration row = require(chatbotId, id);
        boolean next = enabled == null ? !row.isEnabled() : enabled;
        row.setEnabled(next);
        row.setStatus(row.isTelegram() ? (next ? ChannelIntegration.CONNECTED : ChannelIntegration.DISCONNECTED) : (next ? ChannelIntegration.LIVE : ChannelIntegration.OFF));
        return respond(List.of(channels.save(row)), bot).get(0);
    }

    @Transactional
    public ChannelResponse regenerate(UUID chatbotId, UUID id, UserPrincipal principal) {
        ChatbotSummary bot = chatbots.require(chatbotId, principal);
        ChannelIntegration row = require(chatbotId, id);
        if (row.isTelegram()) throw AppException.badRequest("Telegram connections are reconnected, not regenerated.");
        row.setPublicId(randomId(16));
        row.setWebhookSecret(randomId(24));
        return respond(List.of(channels.save(row)), bot).get(0);
    }

    @Transactional
    public ChannelResponse connectTelegram(UUID chatbotId, UUID id, String token, UserPrincipal principal) {
        ChatbotSummary bot = chatbots.require(chatbotId, principal);
        ChannelIntegration row = require(chatbotId, id);
        if (!row.isTelegram()) throw AppException.badRequest("Only Telegram channels accept a bot token.");
        connect(row, token);
        return respond(List.of(channels.save(row)), bot).get(0);
    }

    @Transactional
    public ChannelResponse testTelegram(UUID chatbotId, UUID id, UserPrincipal principal) {
        ChatbotSummary bot = chatbots.require(chatbotId, principal);
        ChannelIntegration row = require(chatbotId, id);
        if (!row.isTelegram() || row.getCredential() == null) throw AppException.badRequest("Connect a Telegram bot first.");
        try {
            TelegramClient.BotInfo info = telegram.getMe(cipher.decrypt(row.getCredential()));
            row.setTelegramUsername(info.username());
            row.setStatus(row.isEnabled() ? ChannelIntegration.CONNECTED : ChannelIntegration.DISCONNECTED);
        } catch (RuntimeException ex) {
            row.setStatus(ChannelIntegration.ERROR);
        }
        return respond(List.of(channels.save(row)), bot).get(0);
    }

    @Transactional
    public void delete(UUID chatbotId, UUID id, UserPrincipal principal) {
        chatbots.require(chatbotId, principal);
        ChannelIntegration row = require(chatbotId, id);
        if (row.isTelegram() && row.getCredential() != null) telegram.deleteWebhook(cipher.decrypt(row.getCredential()));
        channels.delete(row);
    }

    // ----- internal -----

    @Transactional
    public void deleteForChatbot(UUID chatbotId) {
        channels.deleteAll(channels.findByChatbotIdOrderByCreatedAtAsc(chatbotId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> counts(Collection<UUID> chatbotIds) {
        Map<UUID, Long> result = new HashMap<>();
        if (!chatbotIds.isEmpty()) channels.countByChatbots(chatbotIds).forEach(row -> result.put((UUID) row[0], (Long) row[1]));
        return result;
    }

    @Transactional(readOnly = true)
    public boolean telegramConnected(UUID chatbotId) {
        return channels.findByChatbotIdAndChannel(chatbotId, ChannelIntegration.TELEGRAM).map(ChannelIntegration::isEnabled).orElse(false);
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(UUID ownerId) {
        List<NamedCount> byChannel = new ArrayList<>();
        channels.countEnabledByChannel(ownerId).forEach(row -> byChannel.add(new NamedCount((String) row[0], (Long) row[1])));
        return new StatsResponse(ownerId == null ? channels.count() : channels.countByOwnerId(ownerId),
                ownerId == null ? channels.countByEnabledTrue() : channels.countByOwnerIdAndEnabledTrue(ownerId), byChannel);
    }

    // ----- helpers shared with the public service -----

    ChannelSettings settings(ChannelIntegration row, ChatbotSummary bot) {
        ChannelSettings stored = Json.read(row.getSettings(), ChannelSettings.class, null);
        if (stored == null) return ChannelSettings.defaults(bot.name(), "Hello! How can I help you today?");
        return new ChannelSettings(stored.title() == null ? bot.name() : stored.title(), stored.welcome() == null ? "" : stored.welcome(),
                stored.color() == null ? "#0f172a" : stored.color(), stored.position() == null ? "BOTTOM_RIGHT" : stored.position(),
                stored.icon() == null ? "💬" : stored.icon(), stored.domains() == null ? List.of() : stored.domains(),
                stored.starters() == null ? List.of() : stored.starters(), stored.password() == null ? "" : stored.password(),
                stored.expires() == null ? "" : stored.expires());
    }

    static Instant parseExpiry(String value) {
        try {
            return value.length() == 16 ? java.time.LocalDateTime.parse(value).atZone(java.time.ZoneId.systemDefault()).toInstant() : Instant.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public String url(ChannelIntegration row, ChatbotSummary bot) {
        String base = properties.getFrontendUrl().replaceAll("/+$", "");
        return switch (row.getChannel()) {
            case ChannelIntegration.PUBLIC_LINK -> base + "/share/" + slug(bot.name()) + "/" + row.getPublicId();
            case ChannelIntegration.WEB_WIDGET -> base + "/widget/" + row.getPublicId();
            default -> null;
        };
    }

    public String embedCode(ChannelIntegration row, ChannelSettings settings) {
        if (!ChannelIntegration.WEB_WIDGET.equals(row.getChannel()) || !row.isEnabled()) return null;
        String base = properties.getFrontendUrl().replaceAll("/+$", "");
        return "<script src=\"" + base + "/widget.js\" data-widget-id=\"" + row.getPublicId() + "\" data-api=\"" + properties.getPublicApiUrl()
                + "\" data-color=\"" + settings.color() + "\" data-position=\"" + settings.position() + "\" data-icon=\"" + settings.icon() + "\"></script>";
    }

    public String webhookUrl(ChannelIntegration row) {
        return properties.getPublicApiUrl().replaceAll("/+$", "") + "/api/webhooks/telegram/" + row.getPublicId();
    }

    private void connect(ChannelIntegration row, String token) {
        if (token == null || token.isBlank()) throw AppException.badRequest("Enter a demo bot token. Do not use a real secret.");
        TelegramClient.BotInfo info = telegram.getMe(token.trim());
        row.setCredential(cipher.encrypt(token.trim()));
        row.setTelegramBotId(info.id());
        row.setTelegramUsername(info.username());
        if (row.getWebhookSecret() == null) row.setWebhookSecret(randomId(24));
        telegram.setWebhook(token.trim(), webhookUrl(row), row.getWebhookSecret());
        row.setEnabled(true);
        row.setStatus(ChannelIntegration.CONNECTED);
    }

    private List<ChannelResponse> respond(List<ChannelIntegration> rows, ChatbotSummary bot) {
        Map<UUID, Long> weekly = new HashMap<>();
        if (!rows.isEmpty()) {
            messages.countSince(rows.stream().map(ChannelIntegration::getId).toList(), Instant.now().minus(Duration.ofDays(7)))
                    .forEach(row -> weekly.put((UUID) row[0], (Long) row[1]));
        }
        return rows.stream().map(row -> {
            ChannelSettings settings = settings(row, bot);
            String mask = row.getCredential() == null ? null : TokenCipher.mask(cipher.decrypt(row.getCredential()));
            return new ChannelResponse(row.getId(), row.getOwnerId(), row.getChatbotId(), row.getChannel(), row.getPublicId(), row.isEnabled(),
                    row.getStatus(), url(row, bot), embedCode(row, settings), row.getTelegramUsername(), mask, settings,
                    weekly.getOrDefault(row.getId(), 0L), row.getCreatedAt(), row.getUpdatedAt());
        }).toList();
    }

    private ChannelIntegration require(UUID chatbotId, UUID id) {
        return channels.findByIdAndChatbotId(id, chatbotId).orElseThrow(() -> AppException.notFound("Channel not found."));
    }

    private String randomId(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static String slug(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "chatbot" : slug;
    }
}
