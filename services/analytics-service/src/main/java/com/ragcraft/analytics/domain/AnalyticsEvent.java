package com.ragcraft.analytics.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One exchange (question plus answer) on a channel. Owner and chatbot are ids from other services. */
@Entity
@Table(name = "analytics_events")
public class AnalyticsEvent extends BaseEntity {
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "chatbot_id", nullable = false) private UUID chatbotId;
    @Column(nullable = false, length = 12) private String channel;
    @Column(nullable = false) private int messages = 2;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public UUID getChatbotId() { return chatbotId; }
    public void setChatbotId(UUID chatbotId) { this.chatbotId = chatbotId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public int getMessages() { return messages; }
    public void setMessages(int messages) { this.messages = messages; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
