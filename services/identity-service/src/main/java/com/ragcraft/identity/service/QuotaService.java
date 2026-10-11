package com.ragcraft.identity.service;

import com.ragcraft.common.client.ServiceClient;
import com.ragcraft.common.web.AppException;
import com.ragcraft.identity.api.IdentityApi.Limit;
import com.ragcraft.identity.api.IdentityApi.QuotaLimits;
import com.ragcraft.identity.api.IdentityApi.QuotaResponse;
import com.ragcraft.identity.domain.QuotaDefaults;
import com.ragcraft.identity.domain.UserQuota;
import com.ragcraft.identity.repository.QuotaDefaultsRepository;
import com.ragcraft.identity.repository.UserQuotaRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bot and storage limits. Admins can only lower a quota, never go above the platform ceiling. */
@Service
public class QuotaService {

    public static final int MAX_BOTS_CEILING = 5;
    public static final long MAX_STORAGE_CEILING = 5L * 1024 * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final UserQuotaRepository overrides;
    private final QuotaDefaultsRepository defaults;
    private final ServiceClient client;

    public QuotaService(UserQuotaRepository overrides, QuotaDefaultsRepository defaults, ServiceClient client) {
        this.overrides = overrides;
        this.defaults = defaults;
        this.client = client;
    }

    @Transactional(readOnly = true)
    public QuotaLimits limits(UUID userId) {
        return overrides.findByUserId(userId).map(q -> new QuotaLimits(q.getMaxBots(), q.getMaxStorageBytes()))
                .orElseGet(this::defaultLimits);
    }

    private QuotaLimits defaultLimits() {
        return defaults.findFirstByOrderByCreatedAtAsc().map(d -> new QuotaLimits(d.getMaxBots(), d.getMaxStorageBytes()))
                .orElse(new QuotaLimits(MAX_BOTS_CEILING, MAX_STORAGE_CEILING));
    }

    /** Limits plus current usage; usage comes from chatbot-service and knowledge-service and counts as 0 if they are down. */
    public QuotaResponse usage(UUID userId) {
        QuotaLimits limits = limits(userId);
        return new QuotaResponse(new Limit(botCount(userId), limits.maxBots()), new Limit(storageUsed(userId), limits.maxStorageBytes()));
    }

    public long botCount(UUID userId) {
        try {
            Map<UUID, Long> counts = client.asInternal().post().uri(client.urls().getChatbot() + "/api/internal/chatbots/count-by-owner")
                    .body(List.of(userId)).retrieve().body(new ParameterizedTypeReference<Map<UUID, Long>>() {});
            return counts == null ? 0 : counts.getOrDefault(userId, 0L);
        } catch (RuntimeException ex) {
            log.debug("chatbot-service unavailable for quota usage: {}", ex.getMessage());
            return 0;
        }
    }

    public long storageUsed(UUID userId) {
        try {
            Map<String, Long> result = client.asInternal().get().uri(client.urls().getKnowledge() + "/api/internal/owners/{id}/storage", userId)
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Long>>() {});
            return result == null ? 0 : result.getOrDefault("bytes_used", 0L);
        } catch (RuntimeException ex) {
            log.debug("knowledge-service unavailable for quota usage: {}", ex.getMessage());
            return 0;
        }
    }

    @Transactional
    public QuotaLimits set(UUID userId, int maxBots, long maxStorageBytes, UUID adminId) {
        if (maxBots < 1 || maxBots > MAX_BOTS_CEILING) {
            throw AppException.badRequest("Bots must be a whole number from 1 to " + MAX_BOTS_CEILING + ".");
        }
        if (maxStorageBytes < 1 || maxStorageBytes > MAX_STORAGE_CEILING) {
            throw AppException.badRequest("Storage must be between 1 byte and 5 GB.");
        }
        UserQuota quota = overrides.findByUserId(userId).orElseGet(() -> {
            UserQuota created = new UserQuota();
            created.setUserId(userId);
            return created;
        });
        quota.setMaxBots(maxBots);
        quota.setMaxStorageBytes(maxStorageBytes);
        quota.setUpdatedBy(adminId);
        overrides.save(quota);
        return new QuotaLimits(maxBots, maxStorageBytes);
    }

    /** Used when an account is removed so no orphan override stays behind. */
    @Transactional
    public void clear(UUID userId) {
        overrides.findByUserId(userId).ifPresent(overrides::delete);
    }

    QuotaDefaults defaultsRow() {
        return defaults.findFirstByOrderByCreatedAtAsc().orElseGet(QuotaDefaults::new);
    }
}
