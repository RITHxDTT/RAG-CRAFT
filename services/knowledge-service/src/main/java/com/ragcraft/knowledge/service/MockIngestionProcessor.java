package com.ragcraft.knowledge.service;

import com.ragcraft.knowledge.KnowledgeProperties;
import com.ragcraft.knowledge.domain.Document;
import com.ragcraft.knowledge.domain.DocumentChunk;
import com.ragcraft.knowledge.domain.IngestionJob;
import com.ragcraft.knowledge.repository.DocumentChunkRepository;
import com.ragcraft.knowledge.repository.DocumentRepository;
import com.ragcraft.knowledge.repository.IngestionJobRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulated ingestion pipeline. Documents advance one stage every {@code stage-millis}
 * (QUEUED → UPLOADING → PROCESSING → CHUNKING → INDEXING → READY, or the crawling variant for websites).
 * Text files are really chunked so previews and citations show real content; binary formats get
 * placeholder chunks. No embeddings are produced: that is the job of the FastAPI RAG service in a later phase,
 * which will take over when {@code knowledge.processing.mode=external}.
 */
@Component
public class MockIngestionProcessor {

    private static final Logger log = LoggerFactory.getLogger(MockIngestionProcessor.class);

    private final KnowledgeProperties properties;
    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final IngestionJobRepository jobs;
    private final FileStorage storage;

    public MockIngestionProcessor(KnowledgeProperties properties, DocumentRepository documents, DocumentChunkRepository chunks,
                                  IngestionJobRepository jobs, FileStorage storage) {
        this.properties = properties;
        this.documents = documents;
        this.chunks = chunks;
        this.jobs = jobs;
        this.storage = storage;
    }

    public boolean enabled() {
        return "mock".equalsIgnoreCase(properties.getProcessing().getMode());
    }

    @Scheduled(fixedDelayString = "${knowledge.processing.poll-millis:700}")
    public void tick() {
        if (!enabled()) return;
        try {
            advanceAll();
        } catch (RuntimeException ex) {
            log.warn("Mock ingestion tick failed: {}", ex.getMessage());
        }
    }

    @Transactional
    public void advanceAll() {
        for (Document document : documents.findByStatusNotIn(Document.TERMINAL)) {
            advance(document);
        }
    }

    /** Moves the document to the stage matching the elapsed time; completes it when all stages have passed. */
    @Transactional
    public void advance(Document document) {
        if (document.getProcessingStartedAt() == null) {
            document.setProcessingStartedAt(Instant.now());
        }
        List<String> stages = document.isWebsite() ? Document.WEBSITE_STAGES : Document.FILE_STAGES;
        long elapsed = Duration.between(document.getProcessingStartedAt(), Instant.now()).toMillis();
        int step = (int) Math.min(stages.size() - 1, elapsed / Math.max(1, properties.getProcessing().getStageMillis()));
        String next = stages.get(step);
        if (next.equals(document.getStatus())) {
            documents.save(document);
            return;
        }
        document.setStatus(next);
        if (Document.READY.equals(next)) {
            complete(document);
        }
        documents.save(document);
        IngestionJob job = new IngestionJob();
        job.setDocumentId(document.getId());
        job.setStatus(next);
        jobs.save(job);
    }

    /** Forces a document through every remaining stage immediately (used by tests and the reprocess path). */
    @Transactional
    public void completeNow(Document document) {
        document.setProcessingStartedAt(Instant.now().minus(Duration.ofHours(1)));
        advance(document);
    }

    private void complete(Document document) {
        chunks.deleteByDocumentId(document.getId());
        List<String> pieces = extractText(document).map(text -> chunk(text)).orElseGet(() -> placeholders(document));
        int index = 0;
        for (String piece : pieces) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(index++);
            chunk.setContent(piece);
            if ("PDF".equals(document.getFileType())) chunk.setPage(1 + chunk.getChunkIndex() / 3);
            if ("XLSX".equals(document.getFileType())) { chunk.setSheet("Sheet1"); chunk.setRowNumber(2 + chunk.getChunkIndex()); }
            chunks.save(chunk);
        }
        document.setChunkCount(pieces.size());
        document.setErrorMessage(null);
    }

    private java.util.Optional<String> extractText(Document document) {
        if (document.getStorageKey() == null) return java.util.Optional.empty();
        String type = document.getFileType();
        if (!"TXT".equals(type) && !"MD".equals(type)) return java.util.Optional.empty();
        try {
            String text = Files.readString(storage.path(document.getStorageKey()), StandardCharsets.UTF_8);
            return text.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(text.replace("\r\n", "\n"));
        } catch (IOException | RuntimeException ex) {
            return java.util.Optional.empty();
        }
    }

    /** Character-window chunking with overlap, split preferably at paragraph or sentence boundaries. */
    List<String> chunk(String text) {
        int size = properties.getChunkSize();
        int overlap = Math.min(properties.getChunkOverlap(), size / 2);
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length() && result.size() < properties.getMaxDocumentChunks()) {
            int end = Math.min(text.length(), start + size);
            if (end < text.length()) {
                int cut = Math.max(text.lastIndexOf("\n\n", end), Math.max(text.lastIndexOf(". ", end), text.lastIndexOf('\n', end)));
                if (cut > start + size / 2) end = cut + 1;
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) result.add(piece);
            if (end >= text.length()) break;
            start = Math.max(end - overlap, start + 1);
        }
        return result;
    }

    private List<String> placeholders(Document document) {
        int count = Math.max(1, (int) Math.min(properties.getMaxDocumentChunks(), document.getSizeBytes() / 6000));
        List<String> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add("Simulated chunk " + (index + 1) + " of " + document.getName()
                    + ". Text extraction for this format runs in the RAG service; this placeholder stands in for the extracted passage.");
        }
        return result;
    }
}
