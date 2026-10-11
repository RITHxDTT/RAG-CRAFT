package com.ragcraft.knowledge.service;

import com.ragcraft.common.client.ChatbotAccess;
import com.ragcraft.common.client.ChatbotSummary;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import com.ragcraft.knowledge.KnowledgeProperties;
import com.ragcraft.knowledge.api.KnowledgeApi.ActivityResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.AvailabilityResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.ChunkResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.DocumentCounts;
import com.ragcraft.knowledge.api.KnowledgeApi.DocumentDetailResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.DocumentResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.JobResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.NamedCount;
import com.ragcraft.knowledge.api.KnowledgeApi.PreviewResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.ReadyDocument;
import com.ragcraft.knowledge.api.KnowledgeApi.StatsResponse;
import com.ragcraft.knowledge.api.KnowledgeApi.UploadPolicy;
import com.ragcraft.knowledge.domain.Document;
import com.ragcraft.knowledge.domain.DocumentChunk;
import com.ragcraft.knowledge.domain.IngestionJob;
import com.ragcraft.knowledge.domain.KnowledgeSource;
import com.ragcraft.knowledge.repository.DocumentChunkRepository;
import com.ragcraft.knowledge.repository.DocumentRepository;
import com.ragcraft.knowledge.repository.IngestionJobRepository;
import com.ragcraft.knowledge.repository.KnowledgeSourceRepository;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeService {

    private final KnowledgeProperties properties;
    private final KnowledgeSourceRepository sources;
    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final IngestionJobRepository jobs;
    private final FileStorage storage;
    private final ChatbotAccess chatbots;
    private final QuotaGuard quota;
    private final MockIngestionProcessor processor;

    public KnowledgeService(KnowledgeProperties properties, KnowledgeSourceRepository sources, DocumentRepository documents,
                            DocumentChunkRepository chunks, IngestionJobRepository jobs, FileStorage storage, ChatbotAccess chatbots,
                            MockIngestionProcessor processor, QuotaGuard quota) {
        this.quota = quota;
        this.properties = properties;
        this.sources = sources;
        this.documents = documents;
        this.chunks = chunks;
        this.jobs = jobs;
        this.storage = storage;
        this.chatbots = chatbots;
        this.processor = processor;
    }

    public UploadPolicy policy() {
        return new UploadPolicy(properties.getMaxUploadSizeMb(), FileValidation.EXTENSIONS, true, processor.enabled());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID chatbotId, UserPrincipal principal) {
        chatbots.require(chatbotId, principal);
        return documents.findByChatbotIdOrderByCreatedAtDesc(chatbotId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public DocumentResponse upload(UUID chatbotId, MultipartFile file, UserPrincipal principal) {
        ChatbotSummary bot = chatbots.require(chatbotId, principal);
        FileValidation.Checked checked = FileValidation.check(file, properties.getMaxUploadSizeMb());
        sources.findByChatbotIdAndSourceTypeAndSourceKey(chatbotId, KnowledgeSource.DOCUMENT, checked.sha256())
                .ifPresent(existing -> { throw AppException.conflict("This file was already uploaded to this chatbot."); });
        // A document with the same name already exists: the caller chooses Replace (PUT .../replace) or Skip.
        if (!documents.findByChatbotIdAndNameIgnoreCase(chatbotId, file.getOriginalFilename()).isEmpty()) {
            throw AppException.withCode(org.springframework.http.HttpStatus.CONFLICT,
                    "\"" + file.getOriginalFilename() + "\" already exists in this chatbot. Choose Replace or Skip.", "DUPLICATE_NAME");
        }
        quota.assertCanStore(bot.ownerId(), file.getSize());

        KnowledgeSource source = new KnowledgeSource();
        source.setChatbotId(chatbotId);
        source.setOwnerId(bot.ownerId());
        source.setSourceType(KnowledgeSource.DOCUMENT);
        source.setSourceKey(checked.sha256());
        sources.save(source);

        Document document = new Document();
        document.setSourceId(source.getId());
        document.setChatbotId(chatbotId);
        document.setOwnerId(bot.ownerId());
        document.setName(file.getOriginalFilename());
        document.setFileType(checked.extension().toUpperCase());
        document.setMimeType(checked.mimeType());
        document.setSizeBytes(file.getSize());
        document.setStorageKey(storage.save(new ByteArrayInputStream(checked.bytes()), checked.extension()));
        return toResponse(queue(document));
    }

    @Transactional
    public DocumentResponse website(UUID chatbotId, String name, String rawUrl, UserPrincipal principal) {
        ChatbotSummary bot = chatbots.require(chatbotId, principal);
        URI url;
        try {
            url = new URI(rawUrl.trim());
        } catch (URISyntaxException ex) {
            throw AppException.badRequest("Use an HTTP or HTTPS website URL.");
        }
        if (url.getScheme() == null || !List.of("http", "https").contains(url.getScheme().toLowerCase()) || url.getHost() == null) {
            throw AppException.badRequest("Use an HTTP or HTTPS website URL.");
        }
        sources.findByChatbotIdAndSourceTypeAndSourceKey(chatbotId, KnowledgeSource.WEBSITE, url.toString())
                .ifPresent(existing -> { throw AppException.conflict("This website was already added to this chatbot."); });
        quota.assertCanStore(bot.ownerId(), 48_000);
        KnowledgeSource source = new KnowledgeSource();
        source.setChatbotId(chatbotId);
        source.setOwnerId(bot.ownerId());
        source.setSourceType(KnowledgeSource.WEBSITE);
        source.setSourceKey(url.toString());
        source.setUrl(url.toString());
        sources.save(source);

        Document document = new Document();
        document.setSourceId(source.getId());
        document.setChatbotId(chatbotId);
        document.setOwnerId(bot.ownerId());
        document.setName(name.trim());
        document.setFileType("WEBSITE");
        document.setMimeType("text/html");
        document.setSizeBytes(48_000);
        return toResponse(queue(document));
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse detail(UUID chatbotId, UUID documentId, UserPrincipal principal) {
        Document document = require(chatbotId, documentId, principal);
        List<JobResponse> history = jobs.findByDocumentIdOrderByCreatedAtAsc(documentId).stream()
                .map(job -> new JobResponse(job.getId(), job.getStatus(), job.getErrorMessage(), job.getCreatedAt(), job.getUpdatedAt())).toList();
        DocumentResponse base = toResponse(document);
        return new DocumentDetailResponse(base.id(), base.ownerId(), base.chatbotId(), base.name(), base.fileType(), base.mimeType(),
                base.sizeBytes(), base.status(), base.errorMessage(), base.chunkCount(), base.url(), base.createdAt(), base.updatedAt(), history);
    }

    @Transactional(readOnly = true)
    public Path download(UUID chatbotId, UUID documentId, UserPrincipal principal) {
        Document document = require(chatbotId, documentId, principal);
        if (document.getStorageKey() == null || !storage.exists(document.getStorageKey())) {
            throw AppException.notFound("The original file is not available for this source.");
        }
        return storage.path(document.getStorageKey());
    }

    @Transactional(readOnly = true)
    public Document documentFor(UUID chatbotId, UUID documentId, UserPrincipal principal) {
        return require(chatbotId, documentId, principal);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(UUID chatbotId, UUID documentId, UserPrincipal principal) {
        Document document = require(chatbotId, documentId, principal);
        return new AvailabilityResponse(document.getStorageKey() != null && storage.exists(document.getStorageKey()));
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(UUID chatbotId, UUID documentId, int limit, UserPrincipal principal) {
        Document document = require(chatbotId, documentId, principal);
        List<ChunkResponse> rows = chunks.findByDocumentIdOrderByChunkIndexAsc(documentId, PageRequest.of(0, Math.max(1, Math.min(limit, 50))))
                .stream().map(KnowledgeService::toChunk).toList();
        return new PreviewResponse(document.getId(), document.getName(), document.getChunkCount(), rows);
    }

    @Transactional
    public DocumentResponse replace(UUID chatbotId, UUID documentId, MultipartFile file, UserPrincipal principal) {
        Document document = require(chatbotId, documentId, principal);
        if (document.isWebsite()) throw AppException.badRequest("Website sources cannot be replaced with a file.");
        FileValidation.Checked checked = FileValidation.check(file, properties.getMaxUploadSizeMb());
        quota.assertCanStore(document.getOwnerId(), file.getSize() - document.getSizeBytes());
        documents.findByChatbotIdAndNameIgnoreCase(chatbotId, file.getOriginalFilename()).stream().filter(other -> !other.getId().equals(documentId)).findAny()
                .ifPresent(other -> { throw AppException.conflict("Another document already uses that name."); });
        storage.delete(document.getStorageKey());
        document.setName(file.getOriginalFilename());
        document.setFileType(checked.extension().toUpperCase());
        document.setMimeType(checked.mimeType());
        document.setSizeBytes(file.getSize());
        document.setStorageKey(storage.save(new ByteArrayInputStream(checked.bytes()), checked.extension()));
        sources.findById(document.getSourceId()).ifPresent(source -> { source.setSourceKey(checked.sha256()); sources.save(source); });
        chunks.deleteByDocumentId(documentId);
        return toResponse(queue(document));
    }

    @Transactional
    public DocumentResponse requeue(UUID chatbotId, UUID documentId, UserPrincipal principal) {
        Document document = require(chatbotId, documentId, principal);
        chunks.deleteByDocumentId(documentId);
        return toResponse(queue(document));
    }

    @Transactional
    public void delete(UUID chatbotId, UUID documentId, UserPrincipal principal) {
        Document document = require(chatbotId, documentId, principal);
        if (Document.PROCESSING.equals(document.getStatus())) throw AppException.conflict("Wait until processing finishes before deleting.");
        remove(document);
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> recent(UserPrincipal principal) {
        List<Document> rows = principal.isAdmin() ? documents.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, 10))
                : documents.findByOwnerIdOrderByUpdatedAtDesc(principal.userId(), PageRequest.of(0, 10));
        Map<UUID, String> names = new HashMap<>();
        return rows.stream().map(document -> new ActivityResponse(document.getId(), document.getOwnerId(), document.getChatbotId(),
                names.computeIfAbsent(document.getChatbotId(), id -> {
                    try { return chatbots.fetch(id).name(); } catch (RuntimeException ex) { return "Chatbot"; }
                }), document.getName(), document.getFileType(), document.getStatus(), document.getChunkCount(),
                document.getCreatedAt(), document.getUpdatedAt())).toList();
    }

    // ----- internal -----

    @Transactional(readOnly = true)
    public Map<UUID, DocumentCounts> counts(Collection<UUID> chatbotIds) {
        Map<UUID, long[]> acc = new HashMap<>();
        if (!chatbotIds.isEmpty()) {
            for (Object[] row : documents.countByChatbotAndStatus(chatbotIds)) {
                long[] slot = acc.computeIfAbsent((UUID) row[0], id -> new long[3]);
                long count = (Long) row[2];
                slot[0] += count;
                if (Document.READY.equals(row[1])) slot[1] += count;
                if (Document.FAILED.equals(row[1])) slot[2] += count;
            }
        }
        Map<UUID, DocumentCounts> result = new HashMap<>();
        acc.forEach((id, slot) -> result.put(id, new DocumentCounts(slot[0], slot[1], slot[2])));
        return result;
    }

    @Transactional(readOnly = true)
    public List<ReadyDocument> readyDocuments(UUID chatbotId, int chunkLimit) {
        return documents.findByChatbotIdAndStatusOrderByCreatedAtDesc(chatbotId, Document.READY).stream().map(document -> {
            KnowledgeSource source = sources.findById(document.getSourceId()).orElse(null);
            List<ChunkResponse> sample = chunks.findByDocumentIdOrderByChunkIndexAsc(document.getId(), PageRequest.of(0, Math.max(1, chunkLimit)))
                    .stream().map(KnowledgeService::toChunk).toList();
            return new ReadyDocument(document.getId(), document.getName(), document.getFileType(), source == null ? null : source.getUrl(),
                    document.getChunkCount(), sample);
        }).toList();
    }

    @Transactional
    public void deleteForChatbot(UUID chatbotId) {
        documents.findByChatbotIdOrderByCreatedAtDesc(chatbotId).forEach(this::remove);
    }

    @Transactional(readOnly = true)
    public long storageUsed(UUID ownerId) { return documents.sumSizeByOwner(ownerId); }

    public StatsResponse stats(UUID ownerId) {
        long total = ownerId == null ? documents.count() : documents.countByOwnerId(ownerId);
        long files = ownerId == null ? documents.countByFileTypeNot("WEBSITE") : documents.countByOwnerIdAndFileTypeNot(ownerId, "WEBSITE");
        long ready = ownerId == null ? documents.countByStatus(Document.READY) : documents.countByOwnerIdAndStatus(ownerId, Document.READY);
        long failed = ownerId == null ? documents.countByStatus(Document.FAILED) : documents.countByOwnerIdAndStatus(ownerId, Document.FAILED);
        List<NamedCount> byType = new ArrayList<>();
        documents.countByFileType(ownerId).forEach(row -> byType.add(new NamedCount((String) row[0], (Long) row[1])));
        List<NamedCount> byStatus = new ArrayList<>();
        documents.countByStatusGrouped(ownerId).forEach(row -> byStatus.add(new NamedCount((String) row[0], (Long) row[1])));
        return new StatsResponse(total, files, ready, failed, byType, byStatus);
    }

    // ----- helpers -----

    private Document queue(Document document) {
        document.setStatus(Document.QUEUED);
        document.setChunkCount(0);
        document.setErrorMessage(null);
        document.setProcessingStartedAt(Instant.now());
        documents.save(document);
        jobs.deleteByDocumentId(document.getId());
        IngestionJob job = new IngestionJob();
        job.setDocumentId(document.getId());
        job.setStatus(Document.QUEUED);
        jobs.save(job);
        return document;
    }

    private void remove(Document document) {
        storage.delete(document.getStorageKey());
        chunks.deleteByDocumentId(document.getId());
        jobs.deleteByDocumentId(document.getId());
        documents.delete(document);
        sources.deleteById(document.getSourceId());
    }

    private Document require(UUID chatbotId, UUID documentId, UserPrincipal principal) {
        chatbots.require(chatbotId, principal);
        return documents.findByIdAndChatbotId(documentId, chatbotId).orElseThrow(() -> AppException.notFound("Knowledge source not found."));
    }

    DocumentResponse toResponse(Document document) {
        String url = document.isWebsite() ? sources.findById(document.getSourceId()).map(KnowledgeSource::getUrl).orElse(null) : null;
        return new DocumentResponse(document.getId(), document.getOwnerId(), document.getChatbotId(), document.getName(), document.getFileType(),
                document.getMimeType(), document.getSizeBytes(), document.getStatus(), document.getErrorMessage(), document.getChunkCount(), url,
                document.getCreatedAt(), document.getUpdatedAt());
    }

    static ChunkResponse toChunk(DocumentChunk chunk) {
        return new ChunkResponse(chunk.getId(), chunk.getDocumentId(), chunk.getChunkIndex(), chunk.getPage(), chunk.getSheet(), chunk.getRowNumber(), chunk.getContent());
    }
}
