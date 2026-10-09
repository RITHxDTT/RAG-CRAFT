package com.ragcraft.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragcraft.common.security.JwtAuthFilter;
import com.ragcraft.common.security.RagCraftProperties;
import com.ragcraft.common.web.ServiceCallException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Thin REST client for service-to-service calls.
 * - {@link #asInternal()} authenticates with the shared internal token (for /api/internal/** endpoints).
 * - {@link #asCaller()} forwards the current user's token so the downstream service applies its own ownership checks.
 * Downstream {"detail": ...} errors are re-thrown with the same status.
 */
@Component
public class ServiceClient {

    private final RagCraftProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public ServiceClient(RagCraftProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = builder
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new ServiceCallException(HttpStatus.valueOf(response.getStatusCode().value()), readDetail(response.getBody()));
                })
                .build();
    }

    public RagCraftProperties.Services urls() {
        return properties.getServices();
    }

    /** Requests authenticated with the internal service token. */
    public RestClient asInternal() {
        return restClient.mutate().defaultHeader(JwtAuthFilter.INTERNAL_HEADER, properties.getInternalToken()).build();
    }

    /** Requests that forward the caller's Authorization header (or session cookie as a Bearer token). */
    public RestClient asCaller() {
        RestClient.Builder builder = restClient.mutate();
        currentToken().ifPresent(token -> builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        return builder.build();
    }

    public Optional<String> currentToken() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                return Optional.of(header.substring(7).trim());
            }
            if (request.getCookies() != null) {
                for (var cookie : request.getCookies()) {
                    if (properties.getJwt().getCookieName().equals(cookie.getName())) {
                        return Optional.of(cookie.getValue());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private String readDetail(java.io.InputStream body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node != null && node.hasNonNull("detail")) {
                return node.get("detail").asText();
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through to the generic message
        }
        return "A dependent service is unavailable. Please try again.";
    }

    public static MediaType json() {
        return MediaType.APPLICATION_JSON;
    }
}
