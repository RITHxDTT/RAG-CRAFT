package com.ragcraft.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class CatalogApi {
    private CatalogApi() {}

    public record ModelRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 20) String provider,
            @NotBlank @Size(max = 200) String modelIdentifier,
            Boolean enabled,
            Boolean isDefault) {}

    public record ModelResponse(UUID id, String name, String provider, String modelIdentifier, boolean enabled, boolean isDefault,
                                Instant createdAt, Instant updatedAt) {}

    public record PromptRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 10000) String prompt, Boolean enabled) {}

    public record PromptResponse(UUID id, String name, String prompt, boolean enabled, Instant createdAt, Instant updatedAt) {}
}
