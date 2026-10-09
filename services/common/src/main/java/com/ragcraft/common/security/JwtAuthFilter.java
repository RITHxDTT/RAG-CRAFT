package com.ragcraft.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the JWT from the Authorization header (Bearer) or the session cookie and
 * populates the Spring Security context. Also accepts the internal service token
 * on /api/internal/** so services can call each other without a user session.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_HEADER = "X-Internal-Token";
    public static final String ROLE_INTERNAL = "INTERNAL";

    private final JwtService jwtService;
    private final RagCraftProperties properties;
    private final List<PrincipalValidator> validators;

    public JwtAuthFilter(JwtService jwtService, RagCraftProperties properties, List<PrincipalValidator> validators) {
        this.jwtService = jwtService;
        this.properties = properties;
        this.validators = validators;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String internal = request.getHeader(INTERNAL_HEADER);
        if (internal != null && internal.equals(properties.getInternalToken())) {
            var auth = new UsernamePasswordAuthenticationToken("internal", null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + ROLE_INTERNAL)));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
            return;
        }
        extractToken(request).flatMap(jwtService::parse)
                .filter(principal -> validators.stream().allMatch(v -> v.isValid(principal)))
                .ifPresent(principal -> {
                    var auth = new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
        chain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7).trim());
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (properties.getJwt().getCookieName().equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    return Optional.of(cookie.getValue());
                }
            }
        }
        return Optional.empty();
    }
}
