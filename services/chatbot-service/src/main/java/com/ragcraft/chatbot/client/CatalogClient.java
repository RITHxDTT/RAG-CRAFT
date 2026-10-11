package com.ragcraft.chatbot.client;

import com.ragcraft.common.client.ServiceClient;
import com.ragcraft.common.web.AppException;
import com.ragcraft.common.web.ServiceCallException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Reads models, embedding models, prompt templates and the admin limits from catalog-service. */
@Component
public class CatalogClient {

    public record Model(UUID id, String name, String modelIdentifier, String kind, boolean enabled) {}
    public record Prompt(UUID id, String name, String prompt, boolean enabled) {}
    /** One advanced setting: the admin's min, max and default. */
    public record Limit(String key, BigDecimal min, BigDecimal max, BigDecimal defaultValue) {}

    private final ServiceClient client;

    public CatalogClient(ServiceClient client) {
        this.client = client;
    }

    /** A chat model that exists and is enabled; embedding models are rejected here. */
    public Model model(UUID id) {
        try {
            Model model = client.asInternal().get().uri(client.urls().getCatalog() + "/api/internal/models/{id}", id).retrieve().body(Model.class);
            if (model == null || !model.enabled() || !"LLM".equals(model.kind())) throw AppException.badRequest("Choose an enabled model from the list your administrator allows.");
            return model;
        } catch (ServiceCallException ex) {
            if (ex.getStatus() == HttpStatus.NOT_FOUND) throw AppException.badRequest("Choose an enabled model from the list your administrator allows.");
            throw ex;
        }
    }

    public Model defaultModel() {
        Model model = client.asInternal().get().uri(client.urls().getCatalog() + "/api/internal/models/default").retrieve().body(Model.class);
        if (model == null) throw AppException.badRequest("No model is configured yet.");
        return model;
    }

    public List<Model> embeddingModels() {
        List<Model> models = client.asInternal().get().uri(client.urls().getCatalog() + "/api/internal/embedding-models").retrieve()
                .body(new ParameterizedTypeReference<List<Model>>() {});
        return models == null ? List.of() : models;
    }

    public Model defaultEmbeddingModel() {
        Model model = client.asInternal().get().uri(client.urls().getCatalog() + "/api/internal/embedding-models/default").retrieve().body(Model.class);
        if (model == null) throw AppException.badRequest("No embedding model is configured yet.");
        return model;
    }

    /** Setting key (temperature, top_k, ...) to its admin-defined limit. */
    public Map<String, Limit> limits() {
        List<Limit> rows = client.asInternal().get().uri(client.urls().getCatalog() + "/api/internal/settings-limits").retrieve()
                .body(new ParameterizedTypeReference<List<Limit>>() {});
        return rows == null ? Map.of() : rows.stream().collect(Collectors.toMap(Limit::key, l -> l));
    }

    public Prompt prompt(UUID id) {
        try {
            Prompt prompt = client.asInternal().get().uri(client.urls().getCatalog() + "/api/internal/prompt-templates/{id}", id).retrieve().body(Prompt.class);
            if (prompt == null || !prompt.enabled()) throw AppException.badRequest("Choose an enabled prompt template.");
            return prompt;
        } catch (ServiceCallException ex) {
            if (ex.getStatus() == HttpStatus.NOT_FOUND) throw AppException.badRequest("Choose an enabled prompt template.");
            throw ex;
        }
    }
}
