package com.ragcraft.catalog.api;

import com.ragcraft.catalog.api.CatalogApi.LimitRequest;
import com.ragcraft.catalog.api.CatalogApi.LimitResponse;
import com.ragcraft.catalog.api.CatalogApi.ModelRequest;
import com.ragcraft.catalog.api.CatalogApi.ModelResponse;
import com.ragcraft.catalog.api.CatalogApi.PromptRequest;
import com.ragcraft.catalog.api.CatalogApi.PromptResponse;
import com.ragcraft.catalog.service.CatalogService;
import com.ragcraft.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same paths as the FastAPI catalog router:
 * users read enabled items at /api/models and /api/prompt-templates,
 * admins manage everything under /api/admin/..., and other services use /api/internal/....
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;
    private final CurrentUser currentUser;

    public CatalogController(CatalogService catalog, CurrentUser currentUser) {
        this.catalog = catalog;
        this.currentUser = currentUser;
    }

    // ----- user -----
    @GetMapping("/models")
    public List<ModelResponse> models() { return catalog.models(false); }

    @GetMapping("/embedding-models")
    public List<ModelResponse> embeddingModels() { return catalog.embeddingModels(false); }

    @GetMapping("/prompt-templates")
    public List<PromptResponse> prompts() { return catalog.prompts(false); }

    /** Min, max and default of every advanced chatbot setting, set by the admin. */
    @GetMapping("/settings-limits")
    public List<LimitResponse> limits() { return catalog.limits(); }

    // ----- admin -----
    @GetMapping("/admin/models")
    public List<ModelResponse> adminModels() { return catalog.models(true); }

    @PostMapping("/admin/models")
    @ResponseStatus(HttpStatus.CREATED)
    public ModelResponse createModel(@Valid @RequestBody ModelRequest request) { return catalog.saveModel(null, request); }

    @PatchMapping("/admin/models/{id}")
    public ModelResponse updateModel(@PathVariable UUID id, @Valid @RequestBody ModelRequest request) { return catalog.saveModel(id, request); }

    @GetMapping("/admin/embedding-models")
    public List<ModelResponse> adminEmbeddingModels() { return catalog.embeddingModels(true); }

    @PutMapping("/admin/settings-limits/{key}")
    public LimitResponse setLimit(@PathVariable String key, @RequestBody LimitRequest request) {
        return catalog.setLimit(key, request, currentUser.requireAdmin().userId());
    }

    @GetMapping("/admin/prompt-templates")
    public List<PromptResponse> adminPrompts() { return catalog.prompts(true); }

    @PostMapping("/admin/prompt-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public PromptResponse createPrompt(@Valid @RequestBody PromptRequest request) { return catalog.savePrompt(null, request); }

    @PatchMapping("/admin/prompt-templates/{id}")
    public PromptResponse updatePrompt(@PathVariable UUID id, @Valid @RequestBody PromptRequest request) { return catalog.savePrompt(id, request); }

    // ----- internal (other services) -----
    @GetMapping("/internal/models/default")
    public ModelResponse defaultModel() { return catalog.defaultModel(); }

    @GetMapping("/internal/embedding-models/default")
    public ModelResponse defaultEmbeddingModel() { return catalog.defaultEmbeddingModel(); }

    @GetMapping("/internal/embedding-models")
    public List<ModelResponse> internalEmbeddingModels() { return catalog.embeddingModels(false); }

    @GetMapping("/internal/settings-limits")
    public List<LimitResponse> internalLimits() { return catalog.limits(); }

    @GetMapping("/internal/models/{id}")
    public ModelResponse internalModel(@PathVariable UUID id) { return catalog.model(id); }

    @GetMapping("/internal/prompt-templates/{id}")
    public PromptResponse internalPrompt(@PathVariable UUID id) { return catalog.prompt(id); }
}
