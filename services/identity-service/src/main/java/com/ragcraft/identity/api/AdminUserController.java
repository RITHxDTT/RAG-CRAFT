package com.ragcraft.identity.api;

import com.ragcraft.identity.api.IdentityApi.AdminUserResponse;
import com.ragcraft.identity.api.IdentityApi.UpdateUserStatusRequest;
import com.ragcraft.identity.service.AdminUserService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin user management; the ADMIN role is enforced by the shared security rules for /api/admin/**. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService admin;

    public AdminUserController(AdminUserService admin) {
        this.admin = admin;
    }

    @GetMapping
    public List<AdminUserResponse> list(@RequestParam(defaultValue = "") String search,
                                        @RequestParam(defaultValue = "0") int offset,
                                        @RequestParam(defaultValue = "100") int limit) {
        return admin.list(search, Math.max(0, offset), limit);
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable UUID id) {
        return admin.get(id);
    }

    @PatchMapping("/{id}")
    public AdminUserResponse update(@PathVariable UUID id, @RequestBody UpdateUserStatusRequest request) {
        return admin.setActive(id, request.isActive() == null || request.isActive());
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable UUID id) {
        admin.delete(id);
        return Map.of("detail", "Local account and its data deleted.");
    }
}
