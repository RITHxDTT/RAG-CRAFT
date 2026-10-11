package com.ragcraft.catalog.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "models")
public class PlatformModel extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String provider = "OLLAMA";

    @Column(name = "model_identifier", nullable = false, length = 200)
    private String modelIdentifier;

    /** LLM answers questions; EMBEDDING indexes documents (its dimensions size the vector store). */
    @Column(nullable = false, length = 10)
    private String kind = "LLM";

    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "is_system_fallback", nullable = false)
    private boolean isSystemFallback = false;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModelIdentifier() { return modelIdentifier; }
    public void setModelIdentifier(String modelIdentifier) { this.modelIdentifier = modelIdentifier; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Integer getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(Integer embeddingDimensions) { this.embeddingDimensions = embeddingDimensions; }
    public boolean isSystemFallback() { return isSystemFallback; }
    public void setSystemFallback(boolean isSystemFallback) { this.isSystemFallback = isSystemFallback; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}
