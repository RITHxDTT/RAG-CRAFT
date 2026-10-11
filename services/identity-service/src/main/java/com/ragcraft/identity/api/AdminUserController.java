package com.ragcraft.identity.api;

import com.ragcraft.common.security.CurrentUser;
import com.ragcraft.identity.api.IdentityApi.AdminUserResponse;
import com.ragcraft.identity.api.IdentityApi.ReasonRequest;
import com.ragcraft.identity.api.IdentityApi.SetQuotaRequest;
import com.ragcraft.identity.service.AdminUserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin user management; the ADMIN role is enforced by the shared security rules for /api/admin/**. Every action needs a reason. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService admin;
    private final CurrentUser currentUser;

    public AdminUserController(AdminUserService admin, CurrentUser currentUser) {
        this.admin = admin;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<AdminUserResponse> list(@RequestParam(defaultValue = "") String search,
                                        @RequestParam(defaultValue = "0") int offset,
                                        @RequestParam(defaultValue = "100") int limit,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(name = "signup_method", required = false) String signupMethod,
                                        @RequestParam(name = "over_quota", defaultValue = "false") boolean overQuota,
                                        @RequestParam(name = "inactive_30d", defaultValue = "false") boolean inactive30d) {
        return admin.list(search, Math.max(0, offset), limit, new AdminUserService.Filter(status, signupMethod, overQuota, inactive30d));
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable UUID id) {
        return admin.get(id);
    }

    @PostMapping("/{id}/suspend")
    public AdminUserResponse suspend(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return admin.suspend(currentUser.requireAdmin(), id, request.reason());
    }

    @PostMapping("/{id}/reactivate")
    public AdminUserResponse reactivate(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return admin.reactivate(currentUser.requireAdmin(), id, request.reason());
    }

    @PostMapping("/{id}/force-logout")
    public Map<String, String> forceLogout(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        admin.forceLogout(currentUser.requireAdmin(), id, request.reason());
        return Map.of("detail", "All sessions signed out.");
    }

    @PutMapping("/{id}/quota")
    public AdminUserResponse setQuota(@PathVariable UUID id, @Valid @RequestBody SetQuotaRequest request) {
        return admin.setQuota(currentUser.requireAdmin(), id, request.maxBots(), request.maxStorageBytes(), request.reason());
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable UUID id) {
        admin.delete(currentUser.requireAdmin(), id);
        return Map.of("detail", "Account and its data deleted.");
    }
}
