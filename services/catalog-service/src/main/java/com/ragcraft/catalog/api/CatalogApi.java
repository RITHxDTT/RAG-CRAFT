package com.ragcraft.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class CatalogApi {
    private CatalogApi() {}

    public record ModelRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 20) String provider,
            @NotBlank @Size(max = 200) String modelIdentifier,
            String kind,
            Integer embeddingDimensions,
            Boolean enabled,
            Boolean isDefault) {}

    public record ModelResponse(UUID id, String name, String provider, String modelIdentifier, String kind, Integer embeddingDimensions,
                                boolean enabled, boolean isDefault, boolean isSystemFallback, Instant createdAt, Instant updatedAt) {}

    public record PromptRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 10000) String prompt, Boolean enabled) {}

    public record PromptResponse(UUID id, String name, String prompt, boolean enabled, Instant createdAt, Instant updatedAt) {}

    public record LimitRequest(BigDecimal min, BigDecimal max, BigDecimal defaultValue) {}

    public record LimitResponse(String key, BigDecimal min, BigDecimal max, BigDecimal defaultValue) {}
}
