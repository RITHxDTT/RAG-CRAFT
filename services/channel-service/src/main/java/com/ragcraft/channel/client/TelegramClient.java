package com.ragcraft.channel.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.ragcraft.channel.ChannelProperties;
import com.ragcraft.common.web.AppException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Telegram Bot API calls. In {@code simulated} mode (the default for the demo) no network call is made:
 * any token is accepted and replies are only recorded. Set channel.telegram-mode=live to call api.telegram.org.
 */
@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);
    private static final int PART = 3000;

    public record BotInfo(String id, String username) {}

    private final ChannelProperties properties;
    private final RestClient client;

    public TelegramClient(ChannelProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.baseUrl("https://api.telegram.org").build();
    }

    public boolean live() {
        return "live".equalsIgnoreCase(properties.getTelegramMode());
    }

    public BotInfo getMe(String token) {
        if (!live()) {
            String id = HexFormat.of().formatHex(token.getBytes()).substring(0, Math.min(10, token.length() * 2));
            return new BotInfo("sim_" + id, "demo_rag_craft_bot");
        }
        JsonNode node = client.get().uri("/bot{token}/getMe", token).retrieve().body(JsonNode.class);
        if (node == null || !node.path("ok").asBoolean()) throw AppException.badRequest("Telegram rejected the bot token.");
        return new BotInfo(node.path("result").path("id").asText(), node.path("result").path("username").asText());
    }

    public void setWebhook(String token, String url, String secret) {
        if (!live()) {
            log.info("Simulated Telegram webhook registration for {}", url);
            return;
        }
        client.post().uri("/bot{token}/setWebhook", token)
                .body(Map.of("url", url, "secret_token", secret, "allowed_updates", List.of("message")))
                .retrieve().toBodilessEntity();
    }

    public void deleteWebhook(String token) {
        if (!live()) return;
        try {
            client.post().uri("/bot{token}/deleteWebhook", token).retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("Telegram deleteWebhook failed: {}", ex.getMessage());
        }
    }

    /** Sends long answers in 3000-character parts; returns the number of parts sent. */
    public int sendMessage(String token, long chatId, String text) {
        int parts = 0;
        for (int start = 0; start < text.length(); start += PART) {
            String part = text.substring(start, Math.min(text.length(), start + PART));
            if (live()) {
                client.post().uri("/bot{token}/sendMessage", token).body(Map.of("chat_id", chatId, "text", part)).retrieve().toBodilessEntity();
            }
            parts++;
        }
        return parts;
    }
}
