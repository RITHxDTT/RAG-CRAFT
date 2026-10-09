package com.ragcraft.gateway;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Transparent reverse proxy: forwards method, path, query string, headers, and body to the owning service
 * and returns its status, headers, and body unchanged. Cookies and Authorization headers pass through,
 * so sessions work exactly as they would against a single backend.
 */
@RestController
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);
    private static final Set<String> HOP_BY_HOP = Set.of("connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length");

    private final RouteTable routes;
    private final RestClient client;

    public ProxyController(RouteTable routes, GatewayProperties properties) {
        this.routes = routes;
        // JDK HttpClient supports every method the frontend uses, including PATCH (HttpURLConnection does not).
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        RouteTable.Match match = routes.resolve(path).orElse(null);
        if (match == null) {
            return error(HttpStatus.NOT_FOUND, "No service handles this path.");
        }
        String query = request.getQueryString();
        URI target = URI.create(match.baseUrl() + path + (query == null ? "" : "?" + query));
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        try {
            return client.method(method).uri(target)
                    .headers(headers -> copyRequestHeaders(request, headers))
                    .body(body)
                    .exchange((req, res) -> {
                        HttpHeaders out = new HttpHeaders();
                        res.getHeaders().forEach((name, values) -> {
                            if (!HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) out.put(name, values);
                        });
                        byte[] payload = StreamUtils.copyToByteArray(res.getBody());
                        return ResponseEntity.status(res.getStatusCode()).headers(out).body(payload);
                    }, false);
        } catch (ResourceAccessException ex) {
            log.warn("{} unreachable for {} {}: {}", match.service(), method, path, ex.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "The " + match.service() + " service is unavailable. Please try again.");
        }
    }

    private static void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        for (String name : Collections.list(request.getHeaderNames())) {
            if (HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) continue;
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        headers.set("X-Forwarded-For", forwardedFor == null ? request.getRemoteAddr() : forwardedFor + ", " + request.getRemoteAddr());
        headers.set("X-Forwarded-Proto", request.getScheme());
        headers.set("X-Forwarded-Host", request.getServerName() + ":" + request.getServerPort());
    }

    private static ResponseEntity<byte[]> error(HttpStatus status, String detail) {
        String json = "{\"detail\":\"" + detail.replace("\"", "'") + "\"}";
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.getBytes());
    }
}
