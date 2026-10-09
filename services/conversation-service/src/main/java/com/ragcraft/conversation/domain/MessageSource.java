package com.ragcraft.conversation.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** A citation attached to an assistant message. Copies the document name and excerpt so history survives document deletion. */
@Entity
@Table(name = "message_sources")
public class MessageSource extends BaseEntity {

    @Column(name = "message_id", nullable = false) private UUID messageId;
    @Column(name = "document_id") private UUID documentId;
    @Column(name = "chunk_id") private UUID chunkId;
    @Column(name = "document_name", nullable = false) private String documentName;
    @Column(nullable = false, columnDefinition = "text") private String excerpt;
    private Integer page;
    @Column(length = 120) private String sheet;
    @Column(name = "row_number") private Integer rowNumber;
    @Column(name = "chunk_index") private Integer chunkIndex;
    @Column(nullable = false) private double score;
    @Column(length = 2048) private String url;

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }
    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }
    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
