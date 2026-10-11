package com.ragcraft.knowledge.service;

import com.ragcraft.common.client.ServiceClient;
import com.ragcraft.common.web.AppException;
import com.ragcraft.knowledge.repository.DocumentRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Blocks uploads that would push an owner over the storage quota set in identity-service. */
@Component
public class QuotaGuard {

    private record Quota(int maxBots, long maxStorageBytes) {}

    private final ServiceClient client;
    private final DocumentRepository documents;

    public QuotaGuard(ServiceClient client, DocumentRepository documents) {
        this.client = client;
        this.documents = documents;
    }

    /** @param additionalBytes new bytes this request adds (for a replacement, the difference to the old file) */
    public void assertCanStore(UUID ownerId, long additionalBytes) {
        Quota quota = client.asInternal().get().uri(client.urls().getIdentity() + "/api/internal/users/{id}/quota", ownerId).retrieve().body(Quota.class);
        long limit = quota == null ? Long.MAX_VALUE : quota.maxStorageBytes();
        if (documents.sumSizeByOwner(ownerId) + additionalBytes > limit) {
            throw AppException.withCode(HttpStatus.FORBIDDEN, "Storage limit reached. Delete documents or ask an administrator to raise your quota.", "QUOTA_STORAGE");
        }
    }
}
