package com.ragcraft.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "channel")
public class ChannelProperties {
    private String encryptionKey = "development-only-integration-key-change-me-32";
    private String telegramMode = "simulated";
    private int guestRateLimitPerMinute = 30;

    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
    public String getTelegramMode() { return telegramMode; }
    public void setTelegramMode(String telegramMode) { this.telegramMode = telegramMode; }
    public int getGuestRateLimitPerMinute() { return guestRateLimitPerMinute; }
    public void setGuestRateLimitPerMinute(int guestRateLimitPerMinute) { this.guestRateLimitPerMinute = guestRateLimitPerMinute; }
}
