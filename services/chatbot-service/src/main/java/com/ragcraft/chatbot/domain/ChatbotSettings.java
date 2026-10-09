package com.ragcraft.chatbot.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "chatbot_settings")
public class ChatbotSettings extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chatbot_id", nullable = false, unique = true)
    private Chatbot chatbot;

    @Column(name = "model_id")
    private UUID modelId;

    @Column(name = "model_name", nullable = false, length = 200)
    private String modelName;

    @Column(name = "prompt_template_id")
    private UUID promptTemplateId;

    @Column(name = "system_instruction", nullable = false, columnDefinition = "text")
    private String systemInstruction = "Answer using the provided knowledge.";

    @Column(name = "custom_instruction", columnDefinition = "text")
    private String customInstruction;

    @Column(nullable = false, length = 20)
    private String tone = "PROFESSIONAL";

    @Column(nullable = false)
    private double temperature = 0.2;

    @Column(name = "answer_length", nullable = false, length = 10)
    private String answerLength = "MEDIUM";

    @Column(name = "top_k", nullable = false)
    private int topK = 5;

    @Column(name = "welcome_message", length = 2000)
    private String welcomeMessage = "Hello! How can I help you today?";

    @Column(name = "fallback_message", length = 2000)
    private String fallbackMessage = "I do not have that information in this knowledge base.";

    @Column(name = "show_citations", nullable = false)
    private boolean showCitations = true;

    public Chatbot getChatbot() { return chatbot; }
    public void setChatbot(Chatbot chatbot) { this.chatbot = chatbot; }
    public UUID getModelId() { return modelId; }
    public void setModelId(UUID modelId) { this.modelId = modelId; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public UUID getPromptTemplateId() { return promptTemplateId; }
    public void setPromptTemplateId(UUID promptTemplateId) { this.promptTemplateId = promptTemplateId; }
    public String getSystemInstruction() { return systemInstruction; }
    public void setSystemInstruction(String systemInstruction) { this.systemInstruction = systemInstruction; }
    public String getCustomInstruction() { return customInstruction; }
    public void setCustomInstruction(String customInstruction) { this.customInstruction = customInstruction; }
    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public String getAnswerLength() { return answerLength; }
    public void setAnswerLength(String answerLength) { this.answerLength = answerLength; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public String getWelcomeMessage() { return welcomeMessage; }
    public void setWelcomeMessage(String welcomeMessage) { this.welcomeMessage = welcomeMessage; }
    public String getFallbackMessage() { return fallbackMessage; }
    public void setFallbackMessage(String fallbackMessage) { this.fallbackMessage = fallbackMessage; }
    public boolean isShowCitations() { return showCitations; }
    public void setShowCitations(boolean showCitations) { this.showCitations = showCitations; }
}
