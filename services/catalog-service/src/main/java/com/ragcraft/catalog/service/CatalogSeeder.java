package com.ragcraft.catalog.service;

import com.ragcraft.catalog.domain.PlatformModel;
import com.ragcraft.catalog.domain.PromptTemplate;
import com.ragcraft.catalog.repository.ModelRepository;
import com.ragcraft.catalog.repository.PromptTemplateRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds the same demo catalog the frontend uses, so chatbots can be configured on first start. */
@Component
public class CatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final ModelRepository models;
    private final PromptTemplateRepository prompts;
    private final boolean seedDefaults;

    public CatalogSeeder(ModelRepository models, PromptTemplateRepository prompts,
                         @Value("${catalog.seed-defaults:true}") boolean seedDefaults) {
        this.models = models;
        this.prompts = prompts;
        this.seedDefaults = seedDefaults;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedDefaults) return;
        if (models.count() == 0) {
            List<String[]> defaults = List.of(
                    new String[] {"Llama 3.2 3B", "llama3.2:3b"},
                    new String[] {"Qwen", "qwen2.5:3b"},
                    new String[] {"Custom Model", "custom"});
            for (int index = 0; index < defaults.size(); index++) {
                PlatformModel model = new PlatformModel();
                model.setName(defaults.get(index)[0]);
                model.setModelIdentifier(defaults.get(index)[1]);
                model.setDefault(index == 0);
                models.save(model);
            }
            log.info("Seeded {} demo models", defaults.size());
        }
        if (prompts.count() == 0) {
            PromptTemplate template = new PromptTemplate();
            template.setName("Helpful assistant");
            template.setPrompt("Answer using the provided knowledge. If the answer is not in the knowledge, say so.");
            prompts.save(template);
            log.info("Seeded default prompt template");
        }
    }
}
