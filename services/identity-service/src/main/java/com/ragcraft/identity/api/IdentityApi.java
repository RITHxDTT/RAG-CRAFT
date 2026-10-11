package com.ragcraft.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Request and response records for identity-service. JSON is snake_case, matching the FastAPI API. */
public final class IdentityApi {
    private IdentityApi() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 1, max = 256) String password,
            String confirmPassword,
            Boolean terms) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, Boolean remember) {}

    public record UpdateProfileRequest(
            @Size(max = 120) String fullName,
            @Email @Size(max = 254) String email,
            @Size(max = 120) String displayName,
            @Size(max = 2000) String bio,
            String avatar,
            String theme,
            String language,
            @Size(max = 64) String timezone,
            String currentPassword,
            String password,
            String confirmPassword) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String password, String confirmPassword) {}

    public record TokenRequest(@NotBlank String token) {}

    public record EmailRequest(@NotBlank @Email String email) {}

    public record DeleteAccountRequest(@NotBlank String confirmEmail) {}

    /** Mirrors the frontend CurrentUser type. */
    public record UserResponse(
            UUID id,
            String email,
            String fullName,
            String displayName,
            String bio,
            String avatar,
            String theme,
            String language,
            String timezone,
            String role,
            boolean isActive,
            String status,
            String signupMethod,
            String pendingEmail,
            Instant deletionRequestedAt,
            UUID organizationId,
            String organizationName,
            Instant createdAt,
            Instant updatedAt) {}

    /** accessToken is null while a new account is still UNVERIFIED; verificationToken is only returned in log mail mode. */
    public record AuthResponse(String accessToken, String tokenType, int expiresIn, UserResponse user, String verificationToken) {}

    public record MessageResponse(String detail, String resetToken) {}

    public record ProfileUpdateResponse(UserResponse user, String verificationToken) {}

    public record Limit(long used, long limit) {}

    public record QuotaResponse(Limit bots, Limit storage) {}

    public record QuotaLimits(int maxBots, long maxStorageBytes) {}

    public record SetQuotaRequest(int maxBots, long maxStorageBytes, @NotBlank String reason) {}

    public record ReasonRequest(@NotBlank String reason) {}

    public record AdminUserResponse(
            UUID id,
            String email,
            String fullName,
            String role,
            boolean isActive,
            boolean builtIn,
            long chatbotCount,
            Instant createdAt,
            String status,
            String signupMethod,
            Instant lastLoginAt,
            QuotaLimits quota,
            long storageUsed,
            boolean overQuota,
            String suspendReason) {}

    /** Compact view other services need (for example owner email in admin monitoring). */
    public record UserSummary(UUID id, String email, String fullName, String role, boolean isActive) {}

    public record StatsResponse(long totalUsers, long activeUsers) {}

    public record AuditResponse(UUID id, UUID adminId, String adminEmail, String action, String targetType, UUID targetId,
                                String targetLabel, String reason, Map<String, Object> details, String sourceService, Instant createdAt) {}

    /** Written by other services (for example chatbot-service when an admin disables a chatbot). */
    public record AuditRequest(UUID adminId, @NotBlank String adminEmail, @NotBlank String action, @NotBlank String targetType, UUID targetId,
                               @NotBlank String targetLabel, @NotBlank String reason, Map<String, Object> details, String sourceService) {}
}
