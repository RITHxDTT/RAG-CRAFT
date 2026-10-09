package com.ragcraft.gateway;

import java.util.LinkedHashMap;
import java.util.Map;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** /health for the gateway itself and /health/ready with the status of every routed service. */
@RestController
public class GatewayHealthController {

    private final GatewayProperties properties;
    private final RestClient client;

    public GatewayHealthController(GatewayProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1500)).build());
        factory.setReadTimeout(Duration.ofMillis(2500));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "api-gateway");
    }

    @GetMapping("/health/ready")
    public Map<String, Object> ready() {
        Map<String, Object> services = new LinkedHashMap<>();
        boolean allUp = true;
        for (Map.Entry<String, String> entry : properties.getServices().entrySet()) {
            String status;
            try {
                client.get().uri(entry.getValue().replaceAll("/+$", "") + "/health/ready").retrieve().toBodilessEntity();
                status = "ok";
            } catch (RuntimeException ex) {
                status = "unavailable";
                allUp = false;
            }
            services.put(entry.getKey(), status);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", allUp ? "ok" : "degraded");
        result.put("service", "api-gateway");
        result.put("services", services);
        return result;
    }
}
