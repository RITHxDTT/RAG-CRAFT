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

    /** Used automatically when the primary provider fails. */
    @Column(name = "fallback_model_id")
    private UUID fallbackModelId;

    @Column(name = "fallback_model_name", length = 200)
    private String fallbackModelName;

    /** Chosen at creation and never changed afterwards (the PostgreSQL schema also blocks it with a trigger). */
    @Column(name = "embedding_model", nullable = false, length = 200)
    private String embeddingModel = "nomic-embed-text";

    @Column(name = "prompt_template_id")
    private UUID promptTemplateId;

    @Column(name = "system_instruction", nullable = false, columnDefinition = "text")
    private String systemInstruction = "Answer using the provided knowledge.";

    @Column(name = "custom_instruction", columnDefinition = "text")
    private String customInstruction;

    @Column(nullable = false, length = 20)
    private String tone = "PROFESSIONAL";

    @Column(nullable = false, length = 5)
    private String formatting = "RICH";

    @Column(nullable = false, length = 4)
    private String language = "AUTO";

    @Column(nullable = false)
    private double temperature = 0.4;

    @Column(name = "answer_length", nullable = false, length = 10)
    private String answerLength = "CONCISE";

    @Column(name = "max_tokens", nullable = false)
    private int maxTokens = 1024;

    @Column(name = "top_k", nullable = false)
    private int topK = 5;

    @Column(name = "search_mode", nullable = false, length = 10)
    private String searchMode = "HYBRID";

    @Column(name = "max_context_tokens", nullable = false)
    private int maxContextTokens = 4096;

    @Column(name = "chunk_size", nullable = false)
    private int chunkSize = 500;

    @Column(name = "chunk_overlap", nullable = false)
    private int chunkOverlap = 50;

    @Column(name = "answer_from_documents_only", nullable = false)
    private boolean answerFromDocumentsOnly = true;

    @Column(name = "welcome_message", length = 2000)
    private String welcomeMessage = "Hello! How can I help you today?";

    /** Optional override. When empty the localized "Sorry, I couldn't find that in my knowledge base." is used. */
    @Column(name = "fallback_message", length = 2000)
    private String fallbackMessage;

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
    public UUID getFallbackModelId() { return fallbackModelId; }
    public void setFallbackModelId(UUID fallbackModelId) { this.fallbackModelId = fallbackModelId; }
    public String getFallbackModelName() { return fallbackModelName; }
    public void setFallbackModelName(String fallbackModelName) { this.fallbackModelName = fallbackModelName; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public String getFormatting() { return formatting; }
    public void setFormatting(String formatting) { this.formatting = formatting; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public String getSearchMode() { return searchMode; }
    public void setSearchMode(String searchMode) { this.searchMode = searchMode; }
    public int getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(int maxContextTokens) { this.maxContextTokens = maxContextTokens; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public boolean isAnswerFromDocumentsOnly() { return answerFromDocumentsOnly; }
    public void setAnswerFromDocumentsOnly(boolean answerFromDocumentsOnly) { this.answerFromDocumentsOnly = answerFromDocumentsOnly; }
    public boolean isShowCitations() { return showCitations; }
    public void setShowCitations(boolean showCitations) { this.showCitations = showCitations; }
}
