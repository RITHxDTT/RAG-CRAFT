package com.ragcraft.chatbot.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "chatbots")
public class Chatbot extends BaseEntity {

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String ERROR = "ERROR";

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description = "";

    @Column(length = 16)
    private String avatar;

    @Column(nullable = false, length = 10)
    private String status = DRAFT;

    /** JSON array of strings; kept as text so the schema works on PostgreSQL and H2 alike. */
    @Column(name = "starter_questions", columnDefinition = "text")
    private String starterQuestions;

    @OneToOne(mappedBy = "chatbot", cascade = CascadeType.ALL, orphanRemoval = true)
    private ChatbotSettings settings;

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStarterQuestions() { return starterQuestions; }
    public void setStarterQuestions(String starterQuestions) { this.starterQuestions = starterQuestions; }
    public ChatbotSettings getSettings() { return settings; }
    public void setSettings(ChatbotSettings settings) { this.settings = settings; if (settings != null) settings.setChatbot(this); }
}
