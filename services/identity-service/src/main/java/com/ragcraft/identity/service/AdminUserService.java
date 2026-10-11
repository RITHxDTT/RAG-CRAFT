package com.ragcraft.identity.service;

import com.ragcraft.common.client.ServiceClient;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import com.ragcraft.identity.api.IdentityApi.AdminUserResponse;
import com.ragcraft.identity.api.IdentityApi.AuditRequest;
import com.ragcraft.identity.api.IdentityApi.QuotaLimits;
import com.ragcraft.identity.api.IdentityApi.StatsResponse;
import com.ragcraft.identity.domain.User;
import com.ragcraft.identity.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final String SOURCE = "identity-service";

    private final UserRepository users;
    private final ServiceClient client;
    private final QuotaService quotas;
    private final AuditService audit;

    public AdminUserService(UserRepository users, ServiceClient client, QuotaService quotas, AuditService audit) {
        this.users = users;
        this.client = client;
        this.quotas = quotas;
        this.audit = audit;
    }

    /** Filters match the admin user list: status, sign-up method, over quota and inactive for 30+ days. */
    public record Filter(String status, String signupMethod, boolean overQuota, boolean inactive30d) {}

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list(String search, int offset, int limit, Filter filter) {
        int size = Math.max(1, Math.min(limit, 100));
        List<User> candidates = users.search(search == null ? "" : search.trim(), PageRequest.of(0, 500));
        Map<UUID, Long> counts = chatbotCounts(candidates.stream().map(User::getId).toList());
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        return candidates.stream()
                .filter(u -> filter.status() == null || filter.status().isBlank() || filter.status().equals(u.getStatus()))
                .filter(u -> filter.signupMethod() == null || filter.signupMethod().isBlank() || filter.signupMethod().equals(u.getSignupMethod()))
                .filter(u -> !filter.inactive30d() || (u.getLastLoginAt() == null ? u.getCreatedAt() : u.getLastLoginAt()).isBefore(cutoff))
                .map(u -> toResponse(u, counts.getOrDefault(u.getId(), 0L)))
                .filter(r -> !filter.overQuota() || r.overQuota())
                .skip(offset).limit(size).toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID id) {
        User user = users.findById(id).orElseThrow(() -> AppException.notFound("User not found."));
        return toResponse(user, chatbotCounts(List.of(id)).getOrDefault(id, 0L));
    }

    /** Suspending signs the user out everywhere and takes their published chatbots offline (guests see 'unavailable'). */
    @Transactional
    public AdminUserResponse suspend(UserPrincipal admin, UUID id, String reason) {
        User user = mutable(admin, id);
        if (User.SUSPENDED.equals(user.getStatus())) throw AppException.conflict("This account is already suspended.");
        record(admin, "SUSPEND_USER", user, reason, Map.of("previous_status", user.getStatus()));
        user.setStatus(User.SUSPENDED);
        user.setSuspendReason(reason.trim());
        user.setSuspendedAt(Instant.now());
        user.setSuspendedBy(admin.userId());
        user.bumpTokenVersion();
        users.save(user);
        return get(id);
    }

    @Transactional
    public AdminUserResponse reactivate(UserPrincipal admin, UUID id, String reason) {
        User user = mutable(admin, id);
        if (!User.SUSPENDED.equals(user.getStatus())) throw AppException.conflict("Only suspended accounts can be reactivated.");
        record(admin, "REACTIVATE_USER", user, reason, Map.of());
        user.setStatus(User.ACTIVE);
        user.setSuspendReason(null);
        user.setSuspendedAt(null);
        user.setSuspendedBy(null);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
        return get(id);
    }

    @Transactional
    public void forceLogout(UserPrincipal admin, UUID id, String reason) {
        User user = users.findById(id).orElseThrow(() -> AppException.notFound("User not found."));
        record(admin, "FORCE_LOGOUT", user, reason, Map.of());
        user.bumpTokenVersion();
        users.save(user);
    }

    @Transactional
    public AdminUserResponse setQuota(UserPrincipal admin, UUID id, int maxBots, long maxStorageBytes, String reason) {
        User user = users.findById(id).orElseThrow(() -> AppException.notFound("User not found."));
        QuotaLimits before = quotas.limits(id);
        QuotaLimits after = quotas.set(id, maxBots, maxStorageBytes, admin.userId());
        record(admin, "SET_QUOTA", user, reason, Map.of(
                "before", Map.of("max_bots", before.maxBots(), "max_storage_bytes", before.maxStorageBytes()),
                "after", Map.of("max_bots", after.maxBots(), "max_storage_bytes", after.maxStorageBytes())));
        return get(id);
    }

    @Transactional
    public void delete(UserPrincipal admin, UUID id) {
        User user = mutable(admin, id);
        erase(user);
    }

    /** Permanently removes the account and everything it owns. Also used by the purge job after the restore window. */
    @Transactional
    public void erase(User user) {
        // Cascade the owner's resources in the other services (best effort, each service owns its data).
        try {
            client.asInternal().delete().uri(client.urls().getChatbot() + "/api/internal/owners/{id}", user.getId()).retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("Could not cascade delete to chatbot-service for user {}: {}", user.getId(), ex.getMessage());
        }
        users.delete(user);
    }

    /** Accounts past their 7-day restore window are erased. Called by {@link AccountPurgeJob}. */
    @Transactional
    public int purgeExpired(Instant cutoff) {
        List<User> expired = users.findByStatusAndDeletionRequestedAtBefore(User.PENDING_DELETION, cutoff);
        expired.forEach(this::erase);
        return expired.size();
    }

    public StatsResponse stats() {
        return new StatsResponse(users.count(), users.countByStatus(User.ACTIVE));
    }

    private User mutable(UserPrincipal admin, UUID id) {
        User user = users.findById(id).orElseThrow(() -> AppException.notFound("User not found."));
        if (AuthService.PROTECTED_EMAILS.contains(user.getEmail())) throw AppException.forbidden("Fixed demo accounts are protected.");
        if (user.getId().equals(admin.userId())) throw AppException.forbidden("You cannot do this to your own account.");
        return user;
    }

    private void record(UserPrincipal admin, String action, User target, String reason, Map<String, Object> details) {
        audit.record(new AuditRequest(admin.userId(), admin.email(), action, "USER", target.getId(), target.getEmail(), reason == null ? "" : reason, details, SOURCE));
    }

    private Map<UUID, Long> chatbotCounts(List<UUID> ownerIds) {
        if (ownerIds.isEmpty()) return Map.of();
        try {
            Map<UUID, Long> result = client.asInternal().post()
                    .uri(client.urls().getChatbot() + "/api/internal/chatbots/count-by-owner")
                    .body(ownerIds)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<UUID, Long>>() {});
            return result == null ? Map.of() : result;
        } catch (RuntimeException ex) {
            log.debug("chatbot-service unavailable for counts: {}", ex.getMessage());
            return Map.of();
        }
    }

    private AdminUserResponse toResponse(User user, long chatbotCount) {
        QuotaLimits limits = quotas.limits(user.getId());
        long storage = quotas.storageUsed(user.getId());
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive(),
                AuthService.PROTECTED_EMAILS.contains(user.getEmail()), chatbotCount, user.getCreatedAt(), user.getStatus(),
                user.getSignupMethod(), user.getLastLoginAt(), limits, storage,
                chatbotCount > limits.maxBots() || storage > limits.maxStorageBytes(), user.getSuspendReason());
    }
}
