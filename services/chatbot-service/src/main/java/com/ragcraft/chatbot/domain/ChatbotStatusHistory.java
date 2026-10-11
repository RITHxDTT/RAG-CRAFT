package com.ragcraft.chatbot.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Append-only history of status changes: who (OWNER, ADMIN or SYSTEM) moved a chatbot from one status to another, and why. */
@Entity
@Table(name = "chatbot_status_history")
public class ChatbotStatusHistory extends BaseEntity {

    @Column(name = "chatbot_id", nullable = false)
    private UUID chatbotId;

    @Column(name = "from_status", length = 10)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 10)
    private String toStatus;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "actor_role", nullable = false, length = 6)
    private String actorRole;

    @Column(columnDefinition = "text")
    private String reason;

    public static ChatbotStatusHistory of(UUID chatbotId, String from, String to, UUID changedBy, String actorRole, String reason) {
        ChatbotStatusHistory row = new ChatbotStatusHistory();
        row.chatbotId = chatbotId;
        row.fromStatus = from;
        row.toStatus = to;
        row.changedBy = changedBy;
        row.actorRole = actorRole;
        row.reason = reason;
        return row;
    }

    public UUID getChatbotId() { return chatbotId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public UUID getChangedBy() { return changedBy; }
    public String getActorRole() { return actorRole; }
    public String getReason() { return reason; }
}
