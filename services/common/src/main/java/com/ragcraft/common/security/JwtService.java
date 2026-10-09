package com.ragcraft.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Issues and validates the platform JWT. Claims mirror the FastAPI backend
 * (sub, org, ver, iss, aud) with an added role and email so services can authorize
 * without calling identity-service on every request.
 */
@Component
public class JwtService {

    private final RagCraftProperties properties;
    private final SecretKey key;

    public JwtService(RagCraftProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(properties.getJwt().getSessionMinutes()));
        return Jwts.builder()
                .subject(principal.userId().toString())
                .issuer(properties.getJwt().getIssuer())
                .audience().add(properties.getJwt().getAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("org", principal.organizationId() == null ? null : principal.organizationId().toString())
                .claim("ver", principal.tokenVersion())
                .claim("role", principal.role())
                .claim("email", principal.email())
                .signWith(key)
                .compact();
    }

    public Optional<UserPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getJwt().getIssuer())
                    .requireAudience(properties.getJwt().getAudience())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String org = claims.get("org", String.class);
            Integer version = claims.get("ver", Integer.class);
            String role = claims.get("role", String.class);
            return Optional.of(new UserPrincipal(
                    UUID.fromString(claims.getSubject()),
                    org == null ? null : UUID.fromString(org),
                    claims.get("email", String.class),
                    role == null ? UserPrincipal.ROLE_USER : role,
                    version == null ? 0 : version));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public int sessionSeconds() {
        return properties.getJwt().getSessionMinutes() * 60;
    }
}
