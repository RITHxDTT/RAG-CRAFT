package com.ragcraft.conversation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private String mode = "mock";
    private String url = "http://localhost:8000";
    private int maxSources = 3;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public int getMaxSources() { return maxSources; }
    public void setMaxSources(int maxSources) { this.maxSources = maxSources; }
}
