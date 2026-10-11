package com.ragcraft.identity.service;

import com.ragcraft.common.security.JwtService;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import com.ragcraft.identity.IdentityProperties;
import com.ragcraft.identity.api.IdentityApi.AuthResponse;
import com.ragcraft.identity.api.IdentityApi.LoginRequest;
import com.ragcraft.identity.api.IdentityApi.MessageResponse;
import com.ragcraft.identity.api.IdentityApi.ProfileUpdateResponse;
import com.ragcraft.identity.api.IdentityApi.RegisterRequest;
import com.ragcraft.identity.api.IdentityApi.ResetPasswordRequest;
import com.ragcraft.identity.api.IdentityApi.UpdateProfileRequest;
import com.ragcraft.identity.api.IdentityApi.UserResponse;
import com.ragcraft.identity.domain.EmailVerification;
import com.ragcraft.identity.domain.Organization;
import com.ragcraft.identity.domain.OrganizationMember;
import com.ragcraft.identity.domain.PasswordReset;
import com.ragcraft.identity.domain.User;
import com.ragcraft.identity.repository.EmailVerificationRepository;
import com.ragcraft.identity.repository.OrganizationMemberRepository;
import com.ragcraft.identity.repository.OrganizationRepository;
import com.ragcraft.identity.repository.PasswordResetRepository;
import com.ragcraft.identity.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String GENERIC_LOGIN_ERROR = "Invalid email or password.";
    /** The fixed demo accounts cannot be deleted or suspended, as in the frontend demo. */
    static final Set<String> PROTECTED_EMAILS = Set.of("admin@gmail.com", "user@gmail.com");
    /** Hash compared when the email is unknown so login timing does not reveal account existence. */
    private final String dummyHash;

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final PasswordResetRepository resets;
    private final EmailVerificationRepository verifications;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final IdentityProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, OrganizationRepository organizations, OrganizationMemberRepository members,
                       PasswordResetRepository resets, EmailVerificationRepository verifications, PasswordEncoder encoder,
                       JwtService jwt, IdentityProperties properties) {
        this.users = users;
        this.organizations = organizations;
        this.members = members;
        this.resets = resets;
        this.verifications = verifications;
        this.encoder = encoder;
        this.jwt = jwt;
        this.properties = properties;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    // ------------------------------------------------------------------ sign up and verification

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (request.confirmPassword() != null && !request.confirmPassword().equals(request.password())) {
            throw AppException.badRequest("Passwords must match.");
        }
        PasswordPolicy.check(request.password(), email);
        if (!Boolean.TRUE.equals(request.terms())) {
            throw AppException.badRequest("Please accept the Terms of Service and Privacy Policy.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw AppException.conflict("This email is already registered.");
        }
        boolean verified = properties.isAutoVerifyEmail();
        User user = createUser(email, request.password(), request.fullName().trim(), UserPrincipal.ROLE_USER, verified);
        log.info("Registered user {} ({})", user.getId(), user.getStatus());
        if (verified) return issue(user, false);
        String token = issueLink(user, EmailVerification.SIGNUP, email);
        return new AuthResponse(null, "bearer", 0, toResponse(user), exposeToken(token));
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerification link = takeLink(token, EmailVerification.SIGNUP);
        User user = requireUser(link.getUserId());
        if (User.UNVERIFIED.equals(user.getStatus())) user.setStatus(User.ACTIVE);
        user.setEmailVerified(true);
        users.save(user);
    }

    /** Always answers the same way so the form cannot be used to discover accounts. */
    @Transactional
    public MessageResponse resendVerification(String email) {
        Optional<User> found = users.findByEmailIgnoreCase(normalize(email)).filter(u -> User.UNVERIFIED.equals(u.getStatus()));
        String token = found.map(u -> issueLink(u, EmailVerification.SIGNUP, u.getEmail())).orElse(null);
        return new MessageResponse("If an unverified account exists, we've sent a new link.", exposeToken(token));
    }

    @Transactional
    public UserResponse confirmEmailChange(String token) {
        EmailVerification link = takeLink(token, EmailVerification.CHANGE_EMAIL);
        User user = requireUser(link.getUserId());
        if (users.existsByEmailIgnoreCase(link.getEmail())) throw AppException.conflict("This email is already registered.");
        user.setEmail(link.getEmail());
        user.setPendingEmail(null);
        user.setEmailVerified(true);
        return toResponse(users.save(user));
    }

    // ------------------------------------------------------------------ sign in

    /**
     * Locks after repeated failures (1, 2, 3 … up to 15 minutes). noRollbackFor keeps the failure counters even though an
     * AppException is thrown to the caller.
     */
    @Transactional(noRollbackFor = AppException.class)
    public AuthResponse login(LoginRequest request) {
        Optional<User> found = users.findByEmailIgnoreCase(normalize(request.email()));
        if (found.isPresent()) {
            Instant until = found.get().getLockedUntil();
            if (until != null && until.isAfter(Instant.now())) throw locked(until);
        }
        boolean matches = encoder.matches(request.password(), found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !matches) {
            found.ifPresent(this::registerFailure);
            throw AppException.withCode(HttpStatus.UNAUTHORIZED, GENERIC_LOGIN_ERROR, "INVALID_CREDENTIALS");
        }
        User user = found.get();
        if (User.SUSPENDED.equals(user.getStatus())) {
            throw AppException.withCode(HttpStatus.FORBIDDEN, "This account is suspended. Reply to the suspension email to appeal.", "SUSPENDED");
        }
        if (User.UNVERIFIED.equals(user.getStatus())) {
            throw AppException.withCode(HttpStatus.FORBIDDEN, "Verify your email before signing in.", "UNVERIFIED");
        }
        user.setFailedAttempts(0);
        user.setLockCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        users.save(user);
        return issue(user, Boolean.TRUE.equals(request.remember()));
    }

    private void registerFailure(User user) {
        int attempts = user.getFailedAttempts() + 1;
        if (attempts >= properties.getMaxFailedAttempts()) {
            int lockCount = user.getLockCount() + 1;
            int minutes = Math.min(properties.getMaxLockMinutes(), lockCount);
            user.setFailedAttempts(0);
            user.setLockCount(lockCount);
            user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(minutes)));
            users.save(user);
            throw locked(user.getLockedUntil());
        }
        user.setFailedAttempts(attempts);
        users.save(user);
    }

    private static AppException locked(Instant until) {
        long minutes = Math.max(1, (Duration.between(Instant.now(), until).toSeconds() + 59) / 60);
        return AppException.withCode(HttpStatus.TOO_MANY_REQUESTS,
                "Too many failed attempts. Try again in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".", "LOCKED");
    }

    @Transactional
    public void logout(UserPrincipal principal) {
        users.findById(principal.userId()).ifPresent(user -> {
            user.bumpTokenVersion();
            users.save(user);
        });
    }

    // ------------------------------------------------------------------ profile

    /** Works for accounts waiting for deletion too, so they can see the restore option. */
    @Transactional(readOnly = true)
    public UserResponse me(UserPrincipal principal) {
        return toResponse(requireUser(principal.userId()));
    }

    @Transactional
    public ProfileUpdateResponse updateProfile(UserPrincipal principal, UpdateProfileRequest request) {
        User user = requireActive(principal.userId());
        String verificationToken = null;
        if (request.email() != null && !request.email().isBlank()) {
            String email = normalize(request.email());
            if (!email.equalsIgnoreCase(user.getEmail())) {
                if (PROTECTED_EMAILS.contains(user.getEmail())) throw AppException.forbidden("Fixed demo account emails cannot be changed.");
                if (users.existsByEmailIgnoreCase(email)) throw AppException.conflict("This email is already registered.");
                // The address only changes after the new one is verified.
                user.setPendingEmail(email);
                verificationToken = exposeToken(issueLink(user, EmailVerification.CHANGE_EMAIL, email));
            }
        }
        if (request.fullName() != null) {
            if (request.fullName().isBlank()) throw AppException.badRequest("Full name is required.");
            user.setFullName(request.fullName().trim());
        }
        if (request.displayName() != null) user.setDisplayName(request.displayName().isBlank() ? null : request.displayName().trim());
        if (request.bio() != null) user.setBio(request.bio());
        if (request.avatar() != null) user.setAvatar(request.avatar().isBlank() ? null : request.avatar());
        if (request.theme() != null) user.setTheme("dark".equals(request.theme()) ? "dark" : "light");
        if (request.language() != null) {
            if (!Set.of("en", "ko", "km").contains(request.language())) throw AppException.badRequest("Language must be en, ko or km.");
            user.setLanguage(request.language());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            try { java.time.ZoneId.of(request.timezone()); } catch (java.time.DateTimeException ex) { throw AppException.badRequest("Unknown timezone."); }
            user.setTimezone(request.timezone());
        }
        if (request.password() != null && !request.password().isBlank()) {
            if (request.currentPassword() == null || !encoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw AppException.badRequest("Current password is incorrect.");
            }
            if (!request.password().equals(request.confirmPassword())) throw AppException.badRequest("Passwords must match.");
            PasswordPolicy.check(request.password(), user.getEmail());
            user.setPasswordHash(encoder.encode(request.password()));
        }
        return new ProfileUpdateResponse(toResponse(users.save(user)), verificationToken);
    }

    // ------------------------------------------------------------------ password reset

    @Transactional
    public MessageResponse forgotPassword(String email) {
        Optional<User> found = users.findByEmailIgnoreCase(normalize(email));
        String generic = "If an account exists, we've sent a link.";
        if (found.isEmpty()) {
            // Same response for unknown accounts so the endpoint does not leak which emails exist.
            return new MessageResponse(generic, null);
        }
        String token = newToken();
        PasswordReset reset = new PasswordReset();
        reset.setUserId(found.get().getId());
        reset.setTokenHash(sha256(token));
        reset.setExpiresAt(Instant.now().plus(Duration.ofMinutes(properties.getLinkMinutes())));
        resets.save(reset);
        if ("log".equals(properties.getMailMode())) {
            log.info("Password reset token for {}: {}", found.get().getEmail(), token);
            return new MessageResponse(generic + " No email is sent in log mail mode.", token);
        }
        return new MessageResponse(generic, null);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.confirmPassword() != null && !request.confirmPassword().equals(request.password())) {
            throw AppException.badRequest("Passwords must match.");
        }
        PasswordReset reset = resets.findByTokenHash(sha256(request.token()))
                .filter(r -> r.getUsedAt() == null && r.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> AppException.badRequest("This reset link has expired. Start again."));
        User user = requireUser(reset.getUserId());
        PasswordPolicy.check(request.password(), user.getEmail());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.bumpTokenVersion(); // signs the account out everywhere
        reset.setUsedAt(Instant.now());
        users.save(user);
        resets.save(reset);
    }

    // ------------------------------------------------------------------ account deletion (7-day restore window)

    @Transactional
    public Instant requestDeletion(UserPrincipal principal, String confirmEmail) {
        User user = requireActive(principal.userId());
        if (PROTECTED_EMAILS.contains(user.getEmail())) throw AppException.forbidden("Fixed demo accounts cannot be deleted.");
        if (!normalize(confirmEmail).equals(user.getEmail())) throw AppException.badRequest("Retype your email address to confirm.");
        user.setStatus(User.PENDING_DELETION);
        user.setDeletionRequestedAt(Instant.now());
        user.bumpTokenVersion();
        users.save(user);
        return user.getDeletionRequestedAt().plus(Duration.ofDays(properties.getDeletionGraceDays()));
    }

    @Transactional
    public UserResponse restore(UserPrincipal principal) {
        User user = requireUser(principal.userId());
        if (!User.PENDING_DELETION.equals(user.getStatus())) throw AppException.conflict("This account is not scheduled for deletion.");
        user.setStatus(User.ACTIVE);
        user.setDeletionRequestedAt(null);
        return toResponse(users.save(user));
    }

    // ------------------------------------------------------------------ helpers

    /** Also used by the seeder and admin creation. */
    @Transactional
    public User createUser(String email, String password, String fullName, String role) {
        return createUser(email, password, fullName, role, true);
    }

    @Transactional
    public User createUser(String email, String password, String fullName, String role, boolean verified) {
        Organization organization = new Organization();
        organization.setName(UserPrincipal.ROLE_ADMIN.equals(role) ? "Platform Administration" : "My Workspace");
        organizations.save(organization);

        User user = new User();
        user.setEmail(normalize(email));
        user.setPasswordHash(encoder.encode(password));
        user.setFullName(fullName);
        user.setRole(role);
        user.setEmailVerified(verified);
        user.setStatus(verified ? User.ACTIVE : User.UNVERIFIED);
        users.save(user);

        OrganizationMember member = new OrganizationMember();
        member.setOrganizationId(organization.getId());
        member.setUserId(user.getId());
        member.setRole(UserPrincipal.ROLE_ADMIN.equals(role) ? "ADMIN" : "MEMBER");
        members.save(member);
        return user;
    }

    public AuthResponse issue(User user, boolean remember) {
        UUID organizationId = members.findFirstByUserIdOrderByCreatedAtAsc(user.getId())
                .map(OrganizationMember::getOrganizationId).orElse(null);
        UserPrincipal principal = new UserPrincipal(user.getId(), organizationId, user.getEmail(), user.getRole(), user.getTokenVersion());
        int minutes = remember ? properties.getRememberMinutes() : jwt.sessionSeconds() / 60;
        return new AuthResponse(jwt.issue(principal, minutes), "bearer", minutes * 60, toResponse(user), null);
    }

    public UserResponse toResponse(User user) {
        Optional<OrganizationMember> membership = members.findFirstByUserIdOrderByCreatedAtAsc(user.getId());
        UUID organizationId = membership.map(OrganizationMember::getOrganizationId).orElse(null);
        String organizationName = organizationId == null ? null
                : organizations.findById(organizationId).map(Organization::getName).orElse(null);
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getDisplayName(), user.getBio(),
                user.getAvatar(), user.getTheme(), user.getLanguage(), user.getTimezone(), user.getRole(), user.isActive(),
                user.getStatus(), user.getSignupMethod(), user.getPendingEmail(), user.getDeletionRequestedAt(), organizationId,
                organizationName, user.getCreatedAt(), user.getUpdatedAt());
    }

    public User requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> AppException.notFound("User not found."));
    }

    private User requireActive(UUID id) {
        User user = requireUser(id);
        if (User.PENDING_DELETION.equals(user.getStatus())) {
            throw AppException.withCode(HttpStatus.FORBIDDEN, "This account is scheduled for deletion. Restore it to continue.", "PENDING_DELETION");
        }
        return user;
    }

    private String issueLink(User user, String kind, String email) {
        verifications.findByUserIdAndKindAndUsedAtIsNull(user.getId(), kind).forEach(old -> {
            old.setUsedAt(Instant.now());
            verifications.save(old);
        });
        String token = newToken();
        EmailVerification link = new EmailVerification();
        link.setUserId(user.getId());
        link.setKind(kind);
        link.setEmail(email);
        link.setTokenHash(sha256(token));
        link.setExpiresAt(Instant.now().plus(Duration.ofMinutes(properties.getLinkMinutes())));
        verifications.save(link);
        if ("log".equals(properties.getMailMode())) log.info("{} token for {}: {}", kind, email, token);
        return token;
    }

    private EmailVerification takeLink(String token, String kind) {
        EmailVerification link = verifications.findByTokenHash(sha256(token)).filter(l -> kind.equals(l.getKind()))
                .orElseThrow(() -> AppException.withCode(HttpStatus.NOT_FOUND, "This verification link is not valid.", "LINK_INVALID"));
        if (link.getUsedAt() != null) throw AppException.withCode(HttpStatus.GONE, "This link was already used.", "LINK_INVALID");
        if (link.getExpiresAt().isBefore(Instant.now())) {
            throw AppException.withCode(HttpStatus.GONE, "This link has expired. Request a new one.", "LINK_EXPIRED");
        }
        link.setUsedAt(Instant.now());
        verifications.save(link);
        return link;
    }

    /** Only log mail mode hands the token back to the caller; a real mailer would send it instead. */
    private String exposeToken(String token) {
        return "log".equals(properties.getMailMode()) ? token : null;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
