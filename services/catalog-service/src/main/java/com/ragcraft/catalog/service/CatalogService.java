package com.ragcraft.catalog.service;

import com.ragcraft.catalog.api.CatalogApi.ModelRequest;
import com.ragcraft.catalog.api.CatalogApi.ModelResponse;
import com.ragcraft.catalog.api.CatalogApi.PromptRequest;
import com.ragcraft.catalog.api.CatalogApi.PromptResponse;
import com.ragcraft.catalog.domain.PlatformModel;
import com.ragcraft.catalog.domain.PromptTemplate;
import com.ragcraft.catalog.repository.ModelRepository;
import com.ragcraft.catalog.repository.PromptTemplateRepository;
import com.ragcraft.common.web.AppException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final ModelRepository models;
    private final PromptTemplateRepository prompts;

    public CatalogService(ModelRepository models, PromptTemplateRepository prompts) {
        this.models = models;
        this.prompts = prompts;
    }

    @Transactional(readOnly = true)
    public List<ModelResponse> models(boolean includeDisabled) {
        return (includeDisabled ? models.findAllByOrderByCreatedAtAsc() : models.findByEnabledTrueOrderByCreatedAtAsc())
                .stream().map(CatalogService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ModelResponse model(UUID id) {
        return toResponse(models.findById(id).orElseThrow(() -> AppException.notFound("Model not found.")));
    }

    @Transactional(readOnly = true)
    public ModelResponse defaultModel() {
        return models.findFirstByIsDefaultTrue().or(() -> models.findByEnabledTrueOrderByCreatedAtAsc().stream().findFirst())
                .map(CatalogService::toResponse)
                .orElseThrow(() -> AppException.notFound("No model is configured yet."));
    }

    @Transactional
    public ModelResponse saveModel(UUID id, ModelRequest request) {
        PlatformModel model = id == null ? new PlatformModel()
                : models.findById(id).orElseThrow(() -> AppException.notFound("Model not found."));
        model.setName(request.name().trim());
        model.setProvider(request.provider() == null || request.provider().isBlank() ? "OLLAMA" : request.provider().toUpperCase());
        model.setModelIdentifier(request.modelIdentifier().trim());
        if (request.enabled() != null) model.setEnabled(request.enabled());
        if (Boolean.TRUE.equals(request.isDefault())) {
            // Only one default model: cleared here because partial unique indexes are PostgreSQL-only.
            models.findByIsDefaultTrue().forEach(other -> { other.setDefault(false); models.save(other); });
            model.setDefault(true);
            model.setEnabled(true);
        } else if (request.isDefault() != null) {
            model.setDefault(false);
        }
        return toResponse(models.save(model));
    }

    @Transactional(readOnly = true)
    public List<PromptResponse> prompts(boolean includeDisabled) {
        return (includeDisabled ? prompts.findAllByOrderByCreatedAtAsc() : prompts.findByEnabledTrueOrderByCreatedAtAsc())
                .stream().map(CatalogService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PromptResponse prompt(UUID id) {
        return toResponse(prompts.findById(id).orElseThrow(() -> AppException.notFound("Prompt template not found.")));
    }

    @Transactional
    public PromptResponse savePrompt(UUID id, PromptRequest request) {
        PromptTemplate template = id == null ? new PromptTemplate()
                : prompts.findById(id).orElseThrow(() -> AppException.notFound("Prompt template not found."));
        template.setName(request.name().trim());
        template.setPrompt(request.prompt());
        if (request.enabled() != null) template.setEnabled(request.enabled());
        return toResponse(prompts.save(template));
    }

    static ModelResponse toResponse(PlatformModel model) {
        return new ModelResponse(model.getId(), model.getName(), model.getProvider(), model.getModelIdentifier(),
                model.isEnabled(), model.isDefault(), model.getCreatedAt(), model.getUpdatedAt());
    }

    static PromptResponse toResponse(PromptTemplate template) {
        return new PromptResponse(template.getId(), template.getName(), template.getPrompt(), template.isEnabled(),
                template.getCreatedAt(), template.getUpdatedAt());
    }
}
