package com.ragcraft.common.web;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness and readiness, in the same shape as the FastAPI /health endpoints. */
@RestController
public class HealthController {

    private final ObjectProvider<DataSource> dataSource;
    private final String serviceName;

    public HealthController(ObjectProvider<DataSource> dataSource, @Value("${spring.application.name:service}") String serviceName) {
        this.dataSource = dataSource;
        this.serviceName = serviceName;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", serviceName);
    }

    @GetMapping("/health/ready")
    public Map<String, Object> ready() {
        String database = "none";
        DataSource source = dataSource.getIfAvailable();
        if (source != null) {
            try (var connection = source.getConnection()) {
                database = connection.isValid(2) ? "ok" : "unavailable";
            } catch (Exception ex) {
                database = "unavailable";
            }
        }
        return Map.of("status", "ok".equals(database) || "none".equals(database) ? "ok" : "degraded", "service", serviceName, "database", database);
    }
}
