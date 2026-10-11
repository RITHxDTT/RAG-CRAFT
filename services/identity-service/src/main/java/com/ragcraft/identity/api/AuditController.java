package com.ragcraft.identity.api;

import com.ragcraft.identity.api.IdentityApi.AuditResponse;
import com.ragcraft.identity.service.AuditService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only admin view of the audit log. There is deliberately no update or delete route. */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<AuditResponse> list(@RequestParam(required = false) String admin, @RequestParam(required = false) String action,
                                    @RequestParam(required = false) String target, @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to, @RequestParam(defaultValue = "200") int limit) {
        return audit.list(admin, action, target, from, to, limit);
    }

    @GetMapping("/{id}")
    public AuditResponse get(@PathVariable UUID id) {
        return audit.get(id);
    }
}
