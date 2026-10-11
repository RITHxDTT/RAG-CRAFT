package com.ragcraft.identity.domain;

import com.ragcraft.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    public static final String UNVERIFIED = "UNVERIFIED", ACTIVE = "ACTIVE", SUSPENDED = "SUSPENDED", PENDING_DELETION = "PENDING_DELETION";

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(length = 2000)
    private String bio;

    @Column(columnDefinition = "text")
    private String avatar;

    @Column(length = 10)
    private String theme;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(nullable = false, length = 5)
    private String language = "en";

    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    /** UNVERIFIED, ACTIVE, SUSPENDED or PENDING_DELETION. The database column is_active is generated from this, so it is not mapped. */
    @Column(nullable = false, length = 20)
    private String status = ACTIVE;

    @Column(name = "signup_method", nullable = false, length = 10)
    private String signupMethod = "EMAIL";

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "lock_count", nullable = false)
    private int lockCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "suspend_reason", columnDefinition = "text")
    private String suspendReason;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspended_by")
    private UUID suspendedBy;

    @Column(name = "pending_email", length = 254)
    private String pendingEmail;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isActive() { return ACTIVE.equals(status); }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSignupMethod() { return signupMethod; }
    public void setSignupMethod(String signupMethod) { this.signupMethod = signupMethod; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public int getLockCount() { return lockCount; }
    public void setLockCount(int lockCount) { this.lockCount = lockCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public String getSuspendReason() { return suspendReason; }
    public void setSuspendReason(String suspendReason) { this.suspendReason = suspendReason; }
    public Instant getSuspendedAt() { return suspendedAt; }
    public void setSuspendedAt(Instant suspendedAt) { this.suspendedAt = suspendedAt; }
    public UUID getSuspendedBy() { return suspendedBy; }
    public void setSuspendedBy(UUID suspendedBy) { this.suspendedBy = suspendedBy; }
    public String getPendingEmail() { return pendingEmail; }
    public void setPendingEmail(String pendingEmail) { this.pendingEmail = pendingEmail; }
    public Instant getDeletionRequestedAt() { return deletionRequestedAt; }
    public void setDeletionRequestedAt(Instant deletionRequestedAt) { this.deletionRequestedAt = deletionRequestedAt; }
    public int getTokenVersion() { return tokenVersion; }
    public void bumpTokenVersion() { this.tokenVersion++; }
}
