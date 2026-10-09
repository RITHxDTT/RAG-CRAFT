package com.ragcraft.identity.api;

import com.ragcraft.common.security.CurrentUser;
import com.ragcraft.common.security.RagCraftProperties;
import com.ragcraft.identity.api.IdentityApi.AuthResponse;
import com.ragcraft.identity.api.IdentityApi.ForgotPasswordRequest;
import com.ragcraft.identity.api.IdentityApi.LoginRequest;
import com.ragcraft.identity.api.IdentityApi.MessageResponse;
import com.ragcraft.identity.api.IdentityApi.RegisterRequest;
import com.ragcraft.identity.api.IdentityApi.ResetPasswordRequest;
import com.ragcraft.identity.api.IdentityApi.UpdateProfileRequest;
import com.ragcraft.identity.api.IdentityApi.UserResponse;
import com.ragcraft.identity.service.AuthService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final CurrentUser currentUser;
    private final RagCraftProperties properties;

    public AuthController(AuthService auth, CurrentUser currentUser, RagCraftProperties properties) {
        this.auth = auth;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = auth.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.SET_COOKIE, sessionCookie(response.accessToken()).toString()).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = auth.login(request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, sessionCookie(response.accessToken()).toString()).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        auth.logout(currentUser.require());
        ResponseCookie cleared = ResponseCookie.from(properties.getJwt().getCookieName(), "").path("/").maxAge(0).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cleared.toString()).body(Map.of("detail", "Signed out."));
    }

    @GetMapping("/me")
    public UserResponse me() {
        return auth.me(currentUser.require());
    }

    @PatchMapping("/me")
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return auth.updateProfile(currentUser.require(), request);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.accepted().body(auth.forgotPassword(request.email()));
    }

    @PostMapping("/reset-password")
    public Map<String, String> reset(@Valid @RequestBody ResetPasswordRequest request) {
        auth.resetPassword(request);
        return Map.of("detail", "Password updated.");
    }

    private ResponseCookie sessionCookie(String token) {
        return ResponseCookie.from(properties.getJwt().getCookieName(), token)
                .httpOnly(true)
                .secure(properties.getJwt().isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(properties.getJwt().getSessionMinutes()))
                .build();
    }
}
