package com.ragcraft.gateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private String corsOrigins = "http://localhost:3000";
    private int connectTimeoutMillis = 3000;
    private int readTimeoutMillis = 60000;
    /** Service name to base URL. */
    private Map<String, String> services = new LinkedHashMap<>();
    /** Ordered route table; the first matching regex wins. */
    private List<Route> routes = new ArrayList<>();

    public static class Route {
        private String pattern;
        private String service;
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String getService() { return service; }
        public void setService(String service) { this.service = service; }
    }

    public String getCorsOrigins() { return corsOrigins; }
    public void setCorsOrigins(String corsOrigins) { this.corsOrigins = corsOrigins; }
    public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
    public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
    public int getReadTimeoutMillis() { return readTimeoutMillis; }
    public void setReadTimeoutMillis(int readTimeoutMillis) { this.readTimeoutMillis = readTimeoutMillis; }
    public Map<String, String> getServices() { return services; }
    public void setServices(Map<String, String> services) { this.services = services; }
    public List<Route> getRoutes() { return routes; }
    public void setRoutes(List<Route> routes) { this.routes = routes; }
}
