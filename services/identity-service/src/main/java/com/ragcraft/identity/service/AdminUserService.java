package com.ragcraft.identity.service;

import com.ragcraft.common.client.ServiceClient;
import com.ragcraft.common.web.AppException;
import com.ragcraft.identity.api.IdentityApi.AdminUserResponse;
import com.ragcraft.identity.api.IdentityApi.StatsResponse;
import com.ragcraft.identity.domain.User;
import com.ragcraft.identity.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    /** The fixed demo accounts cannot be disabled or deleted, as in the frontend demo. */
    static final Set<String> PROTECTED_EMAILS = Set.of("admin@gmail.com", "user@gmail.com");

    private final UserRepository users;
    private final ServiceClient client;

    public AdminUserService(UserRepository users, ServiceClient client) {
        this.users = users;
        this.client = client;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list(String search, int offset, int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        List<User> page = users.search(search == null ? "" : search.trim(), PageRequest.of(offset / size, size));
        Map<UUID, Long> counts = chatbotCounts(page.stream().map(User::getId).toList());
        return page.stream().map(user -> toResponse(user, counts.getOrDefault(user.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID id) {
        User user = users.findById(id).orElseThrow(() -> AppException.notFound("User not found."));
        return toResponse(user, chatbotCounts(List.of(id)).getOrDefault(id, 0L));
    }

    @Transactional
    public AdminUserResponse setActive(UUID id, boolean active) {
        User user = users.findById(id).orElseThrow(() -> AppException.notFound("User not found."));
        if (PROTECTED_EMAILS.contains(user.getEmail())) {
            throw AppException.forbidden("Fixed demo accounts are protected.");
        }
        user.setActive(active);
        user.bumpTokenVersion();
        users.save(user);
        return toResponse(user, chatbotCounts(List.of(id)).getOrDefault(id, 0L));
    }

    @Transactional
    public void delete(UUID id) {
        User user = users.findById(id).orElseThrow(() -> AppException.notFound("User not found."));
        if (PROTECTED_EMAILS.contains(user.getEmail())) {
            throw AppException.forbidden("Fixed demo accounts are protected.");
        }
        // Cascade the owner's resources in the other services (best effort, each service owns its data).
        try {
            client.asInternal().delete().uri(client.urls().getChatbot() + "/api/internal/owners/{id}", id).retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("Could not cascade delete to chatbot-service for user {}: {}", id, ex.getMessage());
        }
        users.delete(user);
    }

    public StatsResponse stats() {
        return new StatsResponse(users.count(), users.countByActiveTrue());
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

    private static AdminUserResponse toResponse(User user, long chatbotCount) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive(),
                PROTECTED_EMAILS.contains(user.getEmail()), chatbotCount, user.getCreatedAt());
    }
}
