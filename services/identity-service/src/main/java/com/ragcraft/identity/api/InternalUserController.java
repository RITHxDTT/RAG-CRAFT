package com.ragcraft.identity.api;

import com.ragcraft.identity.api.IdentityApi.AuditRequest;
import com.ragcraft.identity.api.IdentityApi.QuotaLimits;
import com.ragcraft.identity.api.IdentityApi.StatsResponse;
import com.ragcraft.identity.api.IdentityApi.UserSummary;
import com.ragcraft.identity.domain.User;
import com.ragcraft.identity.repository.UserRepository;
import com.ragcraft.identity.service.AdminUserService;
import com.ragcraft.identity.service.AuditService;
import com.ragcraft.identity.service.QuotaService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for other services, protected by the internal service token. */
@RestController
@RequestMapping("/api/internal")
public class InternalUserController {

    private final UserRepository users;
    private final AdminUserService admin;
    private final QuotaService quotas;
    private final AuditService audit;

    public InternalUserController(UserRepository users, AdminUserService admin, QuotaService quotas, AuditService audit) {
        this.users = users;
        this.admin = admin;
        this.quotas = quotas;
        this.audit = audit;
    }

    @GetMapping("/users/{id}")
    public UserSummary user(@PathVariable UUID id) {
        return users.findById(id).map(InternalUserController::summary)
                .orElseThrow(() -> com.ragcraft.common.web.AppException.notFound("User not found."));
    }

    /** Resolves many ids at once, for example owner emails in admin chatbot monitoring. */
    @PostMapping("/users/lookup")
    public List<UserSummary> lookup(@RequestBody List<UUID> ids) {
        return users.findAllById(ids).stream().map(InternalUserController::summary).toList();
    }

    /** Other services call this (cached for seconds) to reject tokens of suspended, deleted or signed-out accounts. */
    @GetMapping("/users/{id}/session-valid")
    public Map<String, Boolean> sessionValid(@PathVariable UUID id, @RequestParam int version) {
        return Map.of("valid", users.findById(id).filter(u -> User.ACTIVE.equals(u.getStatus())).map(u -> u.getTokenVersion() == version).orElse(false));
    }

    /** chatbot-service and knowledge-service check limits before creating chatbots or storing documents. */
    @GetMapping("/users/{id}/quota")
    public QuotaLimits quota(@PathVariable UUID id) {
        return quotas.limits(id);
    }

    @PostMapping("/audit")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> audit(@Valid @RequestBody AuditRequest request) {
        return Map.of("id", audit.record(request).getId());
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return admin.stats();
    }

    private static UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive());
    }
}
