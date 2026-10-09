package com.ragcraft.identity.api;

import com.ragcraft.identity.api.IdentityApi.StatsResponse;
import com.ragcraft.identity.api.IdentityApi.UserSummary;
import com.ragcraft.identity.domain.User;
import com.ragcraft.identity.repository.UserRepository;
import com.ragcraft.identity.service.AdminUserService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for other services, protected by the internal service token. */
@RestController
@RequestMapping("/api/internal")
public class InternalUserController {

    private final UserRepository users;
    private final AdminUserService admin;

    public InternalUserController(UserRepository users, AdminUserService admin) {
        this.users = users;
        this.admin = admin;
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

    @GetMapping("/stats")
    public StatsResponse stats() {
        return admin.stats();
    }

    private static UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive());
    }
}
