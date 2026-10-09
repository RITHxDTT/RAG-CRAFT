package com.ragcraft.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {
    private String storageDir = "./storage/knowledge";
    private int maxUploadSizeMb = 20;
    private int chunkSize = 800;
    private int chunkOverlap = 100;
    private int maxDocumentChunks = 2000;
    private final Processing processing = new Processing();

    public String getStorageDir() { return storageDir; }
    public void setStorageDir(String storageDir) { this.storageDir = storageDir; }
    public int getMaxUploadSizeMb() { return maxUploadSizeMb; }
    public void setMaxUploadSizeMb(int maxUploadSizeMb) { this.maxUploadSizeMb = maxUploadSizeMb; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public int getMaxDocumentChunks() { return maxDocumentChunks; }
    public void setMaxDocumentChunks(int maxDocumentChunks) { this.maxDocumentChunks = maxDocumentChunks; }
    public Processing getProcessing() { return processing; }

    public static class Processing {
        private String mode = "mock";
        private long stageMillis = 900;
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public long getStageMillis() { return stageMillis; }
        public void setStageMillis(long stageMillis) { this.stageMillis = stageMillis; }
    }
}
