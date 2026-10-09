package com.ragcraft.knowledge.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "knowledge_sources")
public class KnowledgeSource extends BaseEntity {

    public static final String FILE = "FILE";
    public static final String WEBSITE = "WEBSITE";

    @Column(name = "chatbot_id", nullable = false) private UUID chatbotId;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "source_type", nullable = false, length = 10) private String sourceType;
    /** sha256 of the uploaded file, or the normalised URL for websites. */
    @Column(name = "source_key", nullable = false) private String sourceKey;
    @Column(length = 2048) private String url;

    public UUID getChatbotId() { return chatbotId; }
    public void setChatbotId(UUID chatbotId) { this.chatbotId = chatbotId; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
