package com.ragcraft.conversation.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message extends BaseEntity {

    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";

    @Column(name = "conversation_id", nullable = false) private UUID conversationId;
    @Column(nullable = false) private int sequence;
    @Column(nullable = false, length = 10) private String role;
    @Column(nullable = false, columnDefinition = "text") private String content;

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
