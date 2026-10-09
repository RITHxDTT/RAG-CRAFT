package com.ragcraft.chatbot.client;

import com.ragcraft.common.client.ServiceClient;
import com.ragcraft.common.web.AppException;
import com.ragcraft.common.web.ServiceCallException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Reads models and prompt templates from catalog-service. */
@Component
public class CatalogClient {

    public record Model(UUID id, String name, String modelIdentifier, boolean enabled) {}
    public record Prompt(UUID id, String name, String prompt, boolean enabled) {}

    private final ServiceClient client;

    public CatalogClient(ServiceClient client) {
        this.client = client;
    }

    public Model model(UUID id) {
        try {
            Model model = client.asInternal().get().uri(client.urls().getCatalog() + "/api/internal/models/{id}", id).retrieve().body(Model.class);
            if (model == null || !model.enabled()) throw AppException.badRequest("Choose an enabled model.");
            return model;
        } catch (ServiceCallException ex) {
            if (ex.getStatus() == HttpStatus.NOT_FOUND) throw AppException.badRequest("Choose an enabled model.");
            throw ex;
        }
    }

    public Model defaultModel() {
        Model model = client.asInternal().get().uri(client.urls().getCatalog() + "/api/internal/models/default").retrieve().body(Model.class);
        if (model == null) throw AppException.badRequest("No model is configured yet.");
        return model;
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
