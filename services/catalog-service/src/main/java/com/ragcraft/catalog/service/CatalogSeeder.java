package com.ragcraft.catalog.service;

import com.ragcraft.catalog.domain.AdvancedSettingLimit;
import com.ragcraft.catalog.domain.PlatformModel;
import com.ragcraft.catalog.domain.PromptTemplate;
import com.ragcraft.catalog.repository.AdvancedSettingLimitRepository;
import com.ragcraft.catalog.repository.ModelRepository;
import com.ragcraft.catalog.repository.PromptTemplateRepository;
import java.math.BigDecimal;
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
    private final AdvancedSettingLimitRepository limits;
    private final boolean seedDefaults;

    public CatalogSeeder(ModelRepository models, PromptTemplateRepository prompts, AdvancedSettingLimitRepository limits,
                         @Value("${catalog.seed-defaults:true}") boolean seedDefaults) {
        this.models = models;
        this.prompts = prompts;
        this.limits = limits;
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
                if (index == 1) model.setSystemFallback(true);
                models.save(model);
            }
            List<Object[]> embeddings = List.of(new Object[] {"nomic-embed-text", 768, true}, new Object[] {"bge-m3", 1024, false});
            for (Object[] embedding : embeddings) {
                PlatformModel model = new PlatformModel();
                model.setName((String) embedding[0]);
                model.setModelIdentifier((String) embedding[0]);
                model.setKind("EMBEDDING");
                model.setEmbeddingDimensions((Integer) embedding[1]);
                model.setDefault((Boolean) embedding[2]);
                models.save(model);
            }
            log.info("Seeded {} demo models", defaults.size() + embeddings.size());
        }
        if (limits.count() == 0) {
            // key, min, max, default
            Object[][] rows = {{"temperature", 0, 1, 0.4}, {"max_tokens", 128, 4096, 1024}, {"top_k", 1, 20, 5},
                    {"max_context_tokens", 512, 16384, 4096}, {"chunk_size", 100, 2000, 500}, {"chunk_overlap", 0, 500, 50}};
            for (Object[] row : rows) {
                AdvancedSettingLimit limit = new AdvancedSettingLimit();
                limit.setSettingKey((String) row[0]);
                limit.setMinValue(new BigDecimal(row[1].toString()));
                limit.setMaxValue(new BigDecimal(row[2].toString()));
                limit.setDefaultValue(new BigDecimal(row[3].toString()));
                limits.save(limit);
            }
            log.info("Seeded advanced setting limits");
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
