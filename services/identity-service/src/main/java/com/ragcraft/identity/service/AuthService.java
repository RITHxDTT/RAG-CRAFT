package com.ragcraft.identity.service;

import com.ragcraft.common.security.JwtService;
import com.ragcraft.common.security.UserPrincipal;
import com.ragcraft.common.web.AppException;
import com.ragcraft.identity.IdentityProperties;
import com.ragcraft.identity.api.IdentityApi.AuthResponse;
import com.ragcraft.identity.api.IdentityApi.LoginRequest;
import com.ragcraft.identity.api.IdentityApi.MessageResponse;
import com.ragcraft.identity.api.IdentityApi.RegisterRequest;
import com.ragcraft.identity.api.IdentityApi.ResetPasswordRequest;
import com.ragcraft.identity.api.IdentityApi.UpdateProfileRequest;
import com.ragcraft.identity.api.IdentityApi.UserResponse;
import com.ragcraft.identity.domain.Organization;
import com.ragcraft.identity.domain.OrganizationMember;
import com.ragcraft.identity.domain.PasswordReset;
import com.ragcraft.identity.domain.User;
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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    /** Hash compared when the email is unknown so login timing does not reveal account existence. */
    private final String dummyHash;

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final PasswordResetRepository resets;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final IdentityProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, OrganizationRepository organizations, OrganizationMemberRepository members,
                       PasswordResetRepository resets, PasswordEncoder encoder, JwtService jwt, IdentityProperties properties) {
        this.users = users;
        this.organizations = organizations;
        this.members = members;
        this.resets = resets;
        this.encoder = encoder;
        this.jwt = jwt;
        this.properties = properties;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (request.confirmPassword() != null && !request.confirmPassword().equals(request.password())) {
            throw AppException.badRequest("Passwords must match.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw AppException.conflict("This email is already registered.");
        }
        User user = createUser(email, request.password(), request.fullName().trim(), UserPrincipal.ROLE_USER);
        log.info("Registered user {}", user.getId());
        return issue(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<User> found = users.findByEmailIgnoreCase(normalize(request.email()));
        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = encoder.matches(request.password(), hash);
        if (found.isEmpty() || !matches || !found.get().isActive()) {
            throw AppException.unauthorized("Invalid credentials or disabled account.");
        }
        return issue(found.get());
    }

    @Transactional
    public void logout(UserPrincipal principal) {
        users.findById(principal.userId()).ifPresent(user -> {
            user.bumpTokenVersion();
            users.save(user);
        });
    }

    @Transactional(readOnly = true)
    public UserResponse me(UserPrincipal principal) {
        return toResponse(requireUser(principal.userId()));
    }

    @Transactional
    public UserResponse updateProfile(UserPrincipal principal, UpdateProfileRequest request) {
        User user = requireUser(principal.userId());
        if (request.email() != null && !request.email().isBlank()) {
            String email = normalize(request.email());
            if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) {
                throw AppException.conflict("This email is already registered.");
            }
            user.setEmail(email);
        }
        if (request.fullName() != null) {
            if (request.fullName().isBlank()) throw AppException.badRequest("Full name is required.");
            user.setFullName(request.fullName().trim());
        }
        if (request.displayName() != null) user.setDisplayName(request.displayName().isBlank() ? null : request.displayName().trim());
        if (request.bio() != null) user.setBio(request.bio());
        if (request.avatar() != null) user.setAvatar(request.avatar().isBlank() ? null : request.avatar());
        if (request.theme() != null) user.setTheme("dark".equals(request.theme()) ? "dark" : "light");
        if (request.password() != null && !request.password().isBlank()) {
            if (!request.password().equals(request.confirmPassword())) throw AppException.badRequest("Passwords must match.");
            user.setPasswordHash(encoder.encode(request.password()));
        }
        return toResponse(users.save(user));
    }

    @Transactional
    public MessageResponse forgotPassword(String email) {
        Optional<User> found = users.findByEmailIgnoreCase(normalize(email));
        if (found.isEmpty()) {
            // Same response for unknown accounts so the endpoint does not leak which emails exist.
            return new MessageResponse("If that account exists, a reset link has been prepared.", null);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PasswordReset reset = new PasswordReset();
        reset.setUserId(found.get().getId());
        reset.setTokenHash(sha256(token));
        reset.setExpiresAt(Instant.now().plus(RESET_TTL));
        resets.save(reset);
        if ("log".equals(properties.getMailMode())) {
            log.info("Password reset token for {}: {}", found.get().getEmail(), token);
            return new MessageResponse("Reset link prepared. No email is sent in log mode.", token);
        }
        return new MessageResponse("If that account exists, a reset link has been sent.", null);
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
        user.setPasswordHash(encoder.encode(request.password()));
        user.bumpTokenVersion();
        reset.setUsedAt(Instant.now());
        users.save(user);
        resets.save(reset);
    }

    /** Also used by the seeder and admin creation. */
    @Transactional
    public User createUser(String email, String password, String fullName, String role) {
        Organization organization = new Organization();
        organization.setName(UserPrincipal.ROLE_ADMIN.equals(role) ? "Platform Administration" : "My Workspace");
        organizations.save(organization);

        User user = new User();
        user.setEmail(normalize(email));
        user.setPasswordHash(encoder.encode(password));
        user.setFullName(fullName);
        user.setRole(role);
        users.save(user);

        OrganizationMember member = new OrganizationMember();
        member.setOrganizationId(organization.getId());
        member.setUserId(user.getId());
        member.setRole(UserPrincipal.ROLE_ADMIN.equals(role) ? "ADMIN" : "MEMBER");
        members.save(member);
        return user;
    }

    public AuthResponse issue(User user) {
        UUID organizationId = members.findFirstByUserIdOrderByCreatedAtAsc(user.getId())
                .map(OrganizationMember::getOrganizationId).orElse(null);
        UserPrincipal principal = new UserPrincipal(user.getId(), organizationId, user.getEmail(), user.getRole(), user.getTokenVersion());
        return new AuthResponse(jwt.issue(principal), "bearer", jwt.sessionSeconds(), toResponse(user));
    }

    public UserResponse toResponse(User user) {
        Optional<OrganizationMember> membership = members.findFirstByUserIdOrderByCreatedAtAsc(user.getId());
        UUID organizationId = membership.map(OrganizationMember::getOrganizationId).orElse(null);
        String organizationName = organizationId == null ? null
                : organizations.findById(organizationId).map(Organization::getName).orElse(null);
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getDisplayName(), user.getBio(),
                user.getAvatar(), user.getTheme(), user.getRole(), user.isActive(), organizationId, organizationName,
                user.getCreatedAt(), user.getUpdatedAt());
    }

    public User requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> AppException.notFound("User not found."));
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
