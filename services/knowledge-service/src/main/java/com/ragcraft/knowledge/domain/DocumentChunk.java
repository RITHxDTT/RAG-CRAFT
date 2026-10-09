package com.ragcraft.knowledge.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk extends BaseEntity {

    @Column(name = "document_id", nullable = false) private UUID documentId;
    @Column(name = "chunk_index", nullable = false) private int chunkIndex;
    private Integer page;
    @Column(length = 120) private String sheet;
    @Column(name = "row_number") private Integer rowNumber;
    @Column(nullable = false, columnDefinition = "text") private String content;

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }
    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
