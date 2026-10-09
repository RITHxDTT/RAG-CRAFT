package com.ragcraft.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record UpdateProfileRequest(
            @Size(max = 120) String fullName,
            @Email @Size(max = 254) String email,
            @Size(max = 120) String displayName,
            @Size(max = 2000) String bio,
            String avatar,
            String theme,
            String password,
            String confirmPassword) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String password, String confirmPassword) {}

    public record UpdateUserStatusRequest(Boolean isActive) {}

    /** Mirrors the frontend CurrentUser type. */
    public record UserResponse(
            UUID id,
            String email,
            String fullName,
            String displayName,
            String bio,
            String avatar,
            String theme,
            String role,
            boolean isActive,
            UUID organizationId,
            String organizationName,
            Instant createdAt,
            Instant updatedAt) {}

    public record AuthResponse(String accessToken, String tokenType, int expiresIn, UserResponse user) {}

    public record AdminUserResponse(
            UUID id,
            String email,
            String fullName,
            String role,
            boolean isActive,
            boolean builtIn,
            long chatbotCount,
            Instant createdAt) {}

    public record MessageResponse(String detail, String resetToken) {}

    /** Compact view other services need (for example owner email in admin monitoring). */
    public record UserSummary(UUID id, String email, String fullName, String role, boolean isActive) {}

    public record StatsResponse(long totalUsers, long activeUsers) {}
}
