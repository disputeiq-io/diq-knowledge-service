package io.disputeiq.knowledge.infrastructure;

import io.disputeiq.knowledge.application.HybridKnowledgeSearchUseCase;
import io.disputeiq.knowledge.domain.KnowledgeChunkRepository;
import io.disputeiq.platform.ai.DeterministicEmbeddingGenerator;
import io.disputeiq.platform.privacy.PiiRedactor;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Infrastructure wiring for hybrid RAG retrieval and initial GLOBAL rulebook seeding. */
@Configuration
public class KnowledgeConfig {

    @Bean
    public PiiRedactor piiRedactor() {
        return new PiiRedactor();
    }

    @Bean
    public DeterministicEmbeddingGenerator deterministicEmbeddingGenerator() {
        return new DeterministicEmbeddingGenerator();
    }

    @Bean
    public HybridKnowledgeSearchUseCase hybridKnowledgeSearchUseCase(
            KnowledgeChunkRepository repository,
            PiiRedactor piiRedactor,
            DeterministicEmbeddingGenerator embeddingGenerator,
            Clock clock) {
        return new HybridKnowledgeSearchUseCase(repository, piiRedactor, embeddingGenerator, clock);
    }

    @Bean
    public ApplicationRunner seedDefaultRulebook(HybridKnowledgeSearchUseCase useCase) {
        return args -> useCase.seedDefaultRulebookIfMissing();
    }
}
