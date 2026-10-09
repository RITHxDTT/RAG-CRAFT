package com.ragcraft.channel.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** One row per inbound guest or Telegram message, used for the "messages this week" counter. */
@Entity
@Table(name = "channel_messages")
public class ChannelMessage extends BaseEntity {
    @Column(name = "integration_id", nullable = false) private UUID integrationId;
    public UUID getIntegrationId() { return integrationId; }
    public void setIntegrationId(UUID integrationId) { this.integrationId = integrationId; }
}
