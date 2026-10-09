package com.ragcraft.conversation.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation extends BaseEntity {

    @Column(name = "chatbot_id", nullable = false) private UUID chatbotId;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    /** PLAYGROUND, PUBLIC_LINK, WEB_WIDGET or TELEGRAM. */
    @Column(nullable = false, length = 12) private String channel;
    @Column(name = "integration_id") private UUID integrationId;
    @Column(name = "session_hash", length = 64) private String sessionHash;
    @Column(nullable = false, length = 120) private String title;

    public UUID getChatbotId() { return chatbotId; }
    public void setChatbotId(UUID chatbotId) { this.chatbotId = chatbotId; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public UUID getIntegrationId() { return integrationId; }
    public void setIntegrationId(UUID integrationId) { this.integrationId = integrationId; }
    public String getSessionHash() { return sessionHash; }
    public void setSessionHash(String sessionHash) { this.sessionHash = sessionHash; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
