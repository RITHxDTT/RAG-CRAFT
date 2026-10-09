package com.ragcraft.knowledge.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** One row per processing attempt; the document detail shows them as processing history. */
@Entity
@Table(name = "ingestion_jobs")
public class IngestionJob extends BaseEntity {

    @Column(name = "document_id", nullable = false) private UUID documentId;
    @Column(nullable = false, length = 12) private String status;
    @Column(name = "error_message", length = 2000) private String errorMessage;

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
