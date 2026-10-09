package com.ragcraft.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class KnowledgeApi {
    private KnowledgeApi() {}

    public record UploadPolicy(int maxUploadSizeMb, List<String> extensions, boolean processingEnabled, boolean workerRunning) {}

    public record WebsiteRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 2048) String url) {}

    public record JobResponse(UUID id, String status, String errorMessage, Instant createdAt, Instant updatedAt) {}

    public record DocumentResponse(
            UUID id, UUID ownerId, UUID chatbotId, String name, String fileType, String mimeType, long sizeBytes, String status,
            String errorMessage, int chunkCount, String url, Instant createdAt, Instant updatedAt) {}

    public record DocumentDetailResponse(
            UUID id, UUID ownerId, UUID chatbotId, String name, String fileType, String mimeType, long sizeBytes, String status,
            String errorMessage, int chunkCount, String url, Instant createdAt, Instant updatedAt, List<JobResponse> jobs) {}

    public record ActivityResponse(
            UUID id, UUID ownerId, UUID chatbotId, String chatbotName, String name, String fileType, String status, int chunkCount,
            Instant createdAt, Instant updatedAt) {}

    public record ChunkResponse(UUID id, UUID documentId, int chunkIndex, Integer page, String sheet, Integer rowNumber, String content) {}

    public record PreviewResponse(UUID documentId, String name, int chunkCount, List<ChunkResponse> chunks) {}

    public record AvailabilityResponse(boolean available) {}

    public record DocumentCounts(long total, long ready, long failed) {}

    public record NamedCount(String name, long value) {}

    public record StatsResponse(long totalKnowledge, long totalDocuments, long readyDocuments, long failedDocuments,
                                List<NamedCount> byFileType, List<NamedCount> byStatus) {}

    /** READY documents with a sample excerpt, used by conversation-service to build citations. */
    public record ReadyDocument(UUID id, String name, String fileType, String url, int chunkCount, List<ChunkResponse> chunks) {}
}
