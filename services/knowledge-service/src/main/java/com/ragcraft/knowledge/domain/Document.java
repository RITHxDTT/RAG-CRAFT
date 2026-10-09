package com.ragcraft.knowledge.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class Document extends BaseEntity {

    /** Same status vocabulary as the frontend demo so the UI needs no mapping. */
    public static final String QUEUED = "QUEUED", UPLOADING = "UPLOADING", PROCESSING = "PROCESSING", CRAWLING = "CRAWLING",
            EXTRACTING = "EXTRACTING", CHUNKING = "CHUNKING", INDEXING = "INDEXING", READY = "READY", FAILED = "FAILED", REVIEW = "REVIEW";
    public static final List<String> FILE_STAGES = List.of(QUEUED, UPLOADING, PROCESSING, CHUNKING, INDEXING, READY);
    public static final List<String> WEBSITE_STAGES = List.of(QUEUED, CRAWLING, EXTRACTING, CHUNKING, INDEXING, READY);
    public static final List<String> TERMINAL = List.of(READY, FAILED, REVIEW);

    @Column(name = "source_id", nullable = false, unique = true) private UUID sourceId;
    @Column(name = "chatbot_id", nullable = false) private UUID chatbotId;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(nullable = false) private String name;
    @Column(name = "file_type", nullable = false, length = 10) private String fileType;
    @Column(name = "mime_type", nullable = false, length = 120) private String mimeType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "storage_key") private String storageKey;
    @Column(nullable = false, length = 12) private String status = QUEUED;
    @Column(name = "error_message", length = 2000) private String errorMessage;
    @Column(name = "chunk_count", nullable = false) private int chunkCount = 0;
    @Column(name = "processing_started_at") private Instant processingStartedAt;

    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }
    public UUID getChatbotId() { return chatbotId; }
    public void setChatbotId(UUID chatbotId) { this.chatbotId = chatbotId; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public void setProcessingStartedAt(Instant processingStartedAt) { this.processingStartedAt = processingStartedAt; }
    public boolean isWebsite() { return "WEBSITE".equals(fileType); }
    public boolean isTerminal() { return TERMINAL.contains(status); }
}
