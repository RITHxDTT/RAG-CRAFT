package com.ragcraft.chatbot.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.ListIndexBase;

@Entity
@Table(name = "chatbots")
public class Chatbot extends BaseEntity {

    /** DRAFT -> PENDING (first READY document) -> ACTIVE <-> PAUSED; DISABLED is set and lifted by an admin only. */
    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String ACTIVE = "ACTIVE";
    public static final String PAUSED = "PAUSED";
    public static final String DISABLED = "DISABLED";

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 200)
    private String description = "";

    @Column(columnDefinition = "text")
    private String avatar;

    @Column(nullable = false, length = 10)
    private String status = DRAFT;

    /** Reason an admin gave when disabling the chatbot; shown to the owner. */
    @Column(name = "disabled_reason", columnDefinition = "text")
    private String disabledReason;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "disabled_by")
    private UUID disabledBy;

    /** Ordered, at most four. Stored one row per question with a 1-based position. */
    @ElementCollection
    @CollectionTable(name = "chatbot_starter_questions", joinColumns = @JoinColumn(name = "chatbot_id"))
    @OrderColumn(name = "position")
    @ListIndexBase(1)
    @Column(name = "question", nullable = false, length = 300)
    private List<String> starterQuestions = new ArrayList<>();

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
    public String getDisabledReason() { return disabledReason; }
    public void setDisabledReason(String disabledReason) { this.disabledReason = disabledReason; }
    public Instant getDisabledAt() { return disabledAt; }
    public void setDisabledAt(Instant disabledAt) { this.disabledAt = disabledAt; }
    public UUID getDisabledBy() { return disabledBy; }
    public void setDisabledBy(UUID disabledBy) { this.disabledBy = disabledBy; }
    public List<String> getStarterQuestions() { return starterQuestions; }
    public void setStarterQuestions(List<String> questions) {
        this.starterQuestions.clear();
        this.starterQuestions.addAll(questions);
    }
    public ChatbotSettings getSettings() { return settings; }
    public void setSettings(ChatbotSettings settings) {
        this.settings = settings;
        if (settings != null) settings.setChatbot(this);
    }
}
