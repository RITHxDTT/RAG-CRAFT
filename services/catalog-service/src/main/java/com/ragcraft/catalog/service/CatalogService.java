package com.ragcraft.catalog.service;

import com.ragcraft.catalog.api.CatalogApi.LimitRequest;
import com.ragcraft.catalog.api.CatalogApi.LimitResponse;
import com.ragcraft.catalog.api.CatalogApi.ModelRequest;
import com.ragcraft.catalog.api.CatalogApi.ModelResponse;
import com.ragcraft.catalog.api.CatalogApi.PromptRequest;
import com.ragcraft.catalog.api.CatalogApi.PromptResponse;
import com.ragcraft.catalog.domain.AdvancedSettingLimit;
import com.ragcraft.catalog.domain.PlatformModel;
import com.ragcraft.catalog.domain.PromptTemplate;
import com.ragcraft.catalog.repository.AdvancedSettingLimitRepository;
import com.ragcraft.catalog.repository.ModelRepository;
import com.ragcraft.catalog.repository.PromptTemplateRepository;
import com.ragcraft.common.web.AppException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    public static final String LLM = "LLM", EMBEDDING = "EMBEDDING";
    /** Absolute bounds an admin can never exceed, per setting. */
    static final Map<String, BigDecimal[]> ABSOLUTE = Map.of(
            "temperature", bounds(0, 1), "top_k", bounds(1, 20), "max_tokens", bounds(1, 32768),
            "max_context_tokens", bounds(1, 131072), "chunk_size", bounds(50, 8000), "chunk_overlap", bounds(0, 4000));

    private final ModelRepository models;
    private final PromptTemplateRepository prompts;
    private final AdvancedSettingLimitRepository limits;

    public CatalogService(ModelRepository models, PromptTemplateRepository prompts, AdvancedSettingLimitRepository limits) {
        this.models = models;
        this.prompts = prompts;
        this.limits = limits;
    }

    private static BigDecimal[] bounds(int min, int max) { return new BigDecimal[] {BigDecimal.valueOf(min), BigDecimal.valueOf(max)}; }

    /** Chat (LLM) models. Embedding models are listed separately because users pick them once, at creation. */
    @Transactional(readOnly = true)
    public List<ModelResponse> models(boolean includeDisabled) {
        return list(LLM, includeDisabled);
    }

    @Transactional(readOnly = true)
    public List<ModelResponse> embeddingModels(boolean includeDisabled) {
        return list(EMBEDDING, includeDisabled);
    }

    private List<ModelResponse> list(String kind, boolean includeDisabled) {
        return (includeDisabled ? models.findByKindOrderByCreatedAtAsc(kind) : models.findByKindAndEnabledTrueOrderByCreatedAtAsc(kind))
                .stream().map(CatalogService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ModelResponse model(UUID id) {
        return toResponse(models.findById(id).orElseThrow(() -> AppException.notFound("Model not found.")));
    }

    @Transactional(readOnly = true)
    public ModelResponse defaultModel() {
        return models.findFirstByKindAndIsDefaultTrue(LLM).or(() -> models.findByKindAndEnabledTrueOrderByCreatedAtAsc(LLM).stream().findFirst())
                .map(CatalogService::toResponse)
                .orElseThrow(() -> AppException.notFound("No model is configured yet."));
    }

    @Transactional(readOnly = true)
    public ModelResponse defaultEmbeddingModel() {
        return models.findFirstByKindAndIsDefaultTrue(EMBEDDING).or(() -> models.findByKindAndEnabledTrueOrderByCreatedAtAsc(EMBEDDING).stream().findFirst())
                .map(CatalogService::toResponse)
                .orElseThrow(() -> AppException.notFound("No embedding model is configured yet."));
    }

    @Transactional
    public ModelResponse saveModel(UUID id, ModelRequest request) {
        PlatformModel model = id == null ? new PlatformModel()
                : models.findById(id).orElseThrow(() -> AppException.notFound("Model not found."));
        String kind = request.kind() == null || request.kind().isBlank() ? model.getKind() : request.kind().toUpperCase();
        if (!LLM.equals(kind) && !EMBEDDING.equals(kind)) throw AppException.badRequest("Kind must be LLM or EMBEDDING.");
        if (id != null && !kind.equals(model.getKind())) throw AppException.badRequest("A model's kind cannot change after it is created.");
        Integer dimensions = request.embeddingDimensions() != null ? request.embeddingDimensions() : model.getEmbeddingDimensions();
        if (EMBEDDING.equals(kind) && (dimensions == null || dimensions < 1)) throw AppException.badRequest("Embedding models need their vector dimensions.");
        model.setKind(kind);
        model.setEmbeddingDimensions(EMBEDDING.equals(kind) ? dimensions : null);
        model.setName(request.name().trim());
        model.setProvider(request.provider() == null || request.provider().isBlank() ? "OLLAMA" : request.provider().toUpperCase());
        model.setModelIdentifier(request.modelIdentifier().trim());
        if (request.enabled() != null) model.setEnabled(request.enabled());
        if (Boolean.TRUE.equals(request.isDefault())) {
            // Only one default per kind.
            models.findByKindAndIsDefaultTrue(kind).forEach(other -> { other.setDefault(false); models.save(other); });
            models.flush();
            model.setDefault(true);
            model.setEnabled(true);
        } else if (request.isDefault() != null) {
            model.setDefault(false);
        }
        if (!model.isEnabled() && model.isSystemFallback()) model.setSystemFallback(false);
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

    // ----- admin limits for advanced settings -----

    @Transactional(readOnly = true)
    public List<LimitResponse> limits() {
        return limits.findAll().stream().map(CatalogService::toResponse).sorted(java.util.Comparator.comparing(LimitResponse::key)).toList();
    }

    @Transactional
    public LimitResponse setLimit(String key, LimitRequest request, UUID adminId) {
        BigDecimal[] absolute = ABSOLUTE.get(key);
        if (absolute == null) throw AppException.notFound("Unknown setting.");
        if (request.min() == null || request.max() == null || request.defaultValue() == null) throw AppException.badRequest("Provide min, max and default.");
        if (request.min().compareTo(request.defaultValue()) > 0 || request.defaultValue().compareTo(request.max()) > 0
                || request.min().compareTo(absolute[0]) < 0 || request.max().compareTo(absolute[1]) > 0) {
            throw AppException.badRequest(key + ": use min ≤ default ≤ max within " + absolute[0].stripTrailingZeros().toPlainString() + "–" + absolute[1].stripTrailingZeros().toPlainString() + ".");
        }
        AdvancedSettingLimit limit = limits.findBySettingKey(key).orElseGet(() -> {
            AdvancedSettingLimit created = new AdvancedSettingLimit();
            created.setSettingKey(key);
            return created;
        });
        limit.setMinValue(request.min());
        limit.setMaxValue(request.max());
        limit.setDefaultValue(request.defaultValue());
        limit.setUpdatedBy(adminId);
        return toResponse(limits.save(limit));
    }

    static ModelResponse toResponse(PlatformModel model) {
        return new ModelResponse(model.getId(), model.getName(), model.getProvider(), model.getModelIdentifier(), model.getKind(),
                model.getEmbeddingDimensions(), model.isEnabled(), model.isDefault(), model.isSystemFallback(), model.getCreatedAt(), model.getUpdatedAt());
    }

    static PromptResponse toResponse(PromptTemplate template) {
        return new PromptResponse(template.getId(), template.getName(), template.getPrompt(), template.isEnabled(),
                template.getCreatedAt(), template.getUpdatedAt());
    }

    static LimitResponse toResponse(AdvancedSettingLimit l) {
        return new LimitResponse(l.getSettingKey(), l.getMinValue(), l.getMaxValue(), l.getDefaultValue());
    }
}
