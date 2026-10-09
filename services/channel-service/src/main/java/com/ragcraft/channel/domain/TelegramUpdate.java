package com.ragcraft.channel.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Deduplicates Telegram webhook deliveries by update_id, as the FastAPI backend does. */
@Entity
@Table(name = "telegram_updates")
public class TelegramUpdate extends BaseEntity {
    @Column(name = "integration_id", nullable = false) private UUID integrationId;
    @Column(name = "update_id", nullable = false) private long updateId;
    @Column(columnDefinition = "text") private String answer;
    @Column(name = "sent_parts", nullable = false) private int sentParts;

    public UUID getIntegrationId() { return integrationId; }
    public void setIntegrationId(UUID integrationId) { this.integrationId = integrationId; }
    public long getUpdateId() { return updateId; }
    public void setUpdateId(long updateId) { this.updateId = updateId; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public int getSentParts() { return sentParts; }
    public void setSentParts(int sentParts) { this.sentParts = sentParts; }
}
