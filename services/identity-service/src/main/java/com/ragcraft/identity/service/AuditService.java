package com.ragcraft.identity.service;

import com.ragcraft.common.web.AppException;
import com.ragcraft.identity.api.IdentityApi.AuditRequest;
import com.ragcraft.identity.api.IdentityApi.AuditResponse;
import com.ragcraft.identity.domain.AuditLog;
import com.ragcraft.identity.repository.AuditLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every admin action is written here with a mandatory reason. The log is read-only afterwards. */
@Service
public class AuditService {

    public static final Set<String> ACTIONS = Set.of("FORCE_DISABLE_CHATBOT", "REENABLE_CHATBOT", "SUSPEND_USER", "REACTIVATE_USER",
            "APPROVE_APPEAL", "REJECT_APPEAL", "SET_QUOTA", "RESET_MFA", "FORCE_LOGOUT");
    private static final Set<String> TARGETS = Set.of("USER", "CHATBOT", "APPEAL");

    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public AuditLog record(AuditRequest request) {
        if (!ACTIONS.contains(request.action())) throw AppException.badRequest("Unknown audit action.");
        if (!TARGETS.contains(request.targetType())) throw AppException.badRequest("Unknown audit target type.");
        if (request.reason() == null || request.reason().isBlank()) throw AppException.badRequest("A reason is required.");
        if (request.targetId() == null) throw AppException.badRequest("A target is required.");
        AuditLog entry = new AuditLog();
        entry.setAdminId(request.adminId());
        entry.setAdminEmail(request.adminEmail());
        entry.setAction(request.action());
        entry.setTargetType(request.targetType());
        entry.setTargetId(request.targetId());
        entry.setTargetLabel(request.targetLabel());
        entry.setReason(request.reason().trim());
        entry.setDetails(request.details() == null ? Map.of() : request.details());
        entry.setSourceService(request.sourceService());
        return logs.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> list(String admin, String action, String target, String from, String to, int limit) {
        Instant start = from == null || from.isBlank() ? Instant.EPOCH : LocalDate.parse(from).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to == null || to.isBlank() ? Instant.parse("2999-01-01T00:00:00Z") : LocalDate.parse(to).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return logs.search(admin == null ? "" : admin.trim(), action == null ? "" : action.trim(), target == null ? "" : target.trim(),
                start, end, PageRequest.of(0, Math.max(1, Math.min(limit, 500)))).stream().map(AuditService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AuditResponse get(UUID id) {
        return logs.findById(id).map(AuditService::toResponse).orElseThrow(() -> AppException.notFound("Audit entry not found."));
    }

    static AuditResponse toResponse(AuditLog a) {
        return new AuditResponse(a.getId(), a.getAdminId(), a.getAdminEmail(), a.getAction(), a.getTargetType(), a.getTargetId(),
                a.getTargetLabel(), a.getReason(), a.getDetails(), a.getSourceService(), a.getCreatedAt());
    }
}
