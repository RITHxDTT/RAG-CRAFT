package com.ragcraft.gateway;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Resolves a request path to the owning service's base URL using the ordered route table. */
@Component
public class RouteTable {

    public record Match(String service, String baseUrl) {}

    private record Compiled(Pattern pattern, String service) {}

    private final List<Compiled> routes;
    private final GatewayProperties properties;

    public RouteTable(GatewayProperties properties) {
        this.properties = properties;
        this.routes = properties.getRoutes().stream()
                .map(route -> new Compiled(Pattern.compile(route.getPattern()), route.getService()))
                .toList();
        for (Compiled route : routes) {
            if (!properties.getServices().containsKey(route.service())) {
                throw new IllegalStateException("Route points to unknown service: " + route.service());
            }
        }
    }

    public Optional<Match> resolve(String path) {
        // Internal endpoints are never reachable through the gateway.
        if (path.startsWith("/api/internal")) return Optional.empty();
        for (Compiled route : routes) {
            if (route.pattern().matcher(path).matches()) {
                return Optional.of(new Match(route.service(), properties.getServices().get(route.service()).replaceAll("/+$", "")));
            }
        }
        return Optional.empty();
    }
}
