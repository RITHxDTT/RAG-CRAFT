package com.ragcraft.channel.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "channel_integrations")
public class ChannelIntegration extends BaseEntity {

    public static final String PUBLIC_LINK = "PUBLIC_LINK", WEB_WIDGET = "WEB_WIDGET", TELEGRAM = "TELEGRAM";
    public static final String LIVE = "LIVE", OFF = "OFF", CONNECTED = "CONNECTED", DISCONNECTED = "DISCONNECTED", ERROR = "ERROR";

    @Column(name = "chatbot_id", nullable = false) private UUID chatbotId;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 12) private String channel;
    @Column(name = "public_id", nullable = false, unique = true, length = 64) private String publicId;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false, length = 12) private String status;
    /** JSON of ChannelSettings (title, welcome, color, position, icon, domains, starters, password, expires). */
    @Column(columnDefinition = "text") private String settings;
    /** Encrypted Telegram bot token; never returned by the API. */
    @Column(columnDefinition = "text") private String credential;
    @Column(name = "webhook_secret", length = 64) private String webhookSecret;
    @Column(name = "telegram_bot_id", length = 40) private String telegramBotId;
    @Column(name = "telegram_username", length = 80) private String telegramUsername;

    public UUID getChatbotId() { return chatbotId; }
    public void setChatbotId(UUID chatbotId) { this.chatbotId = chatbotId; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }
    public String getCredential() { return credential; }
    public void setCredential(String credential) { this.credential = credential; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public String getTelegramBotId() { return telegramBotId; }
    public void setTelegramBotId(String telegramBotId) { this.telegramBotId = telegramBotId; }
    public String getTelegramUsername() { return telegramUsername; }
    public void setTelegramUsername(String telegramUsername) { this.telegramUsername = telegramUsername; }
    public boolean isTelegram() { return TELEGRAM.equals(channel); }
}
