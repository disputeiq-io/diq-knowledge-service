package io.disputeiq.knowledge.application;

import io.disputeiq.knowledge.domain.SourceType;
import java.util.Objects;

/** Command to redact, embed, and index a policy or rulebook chunk. */
public record IngestKnowledgeCommand(
        String ruleCode,
        String title,
        String network,
        String reasonCode,
        SourceType sourceType,
        String headingPath,
        String rawContent,
        boolean globalRulebook) {

    public IngestKnowledgeCommand {
        Objects.requireNonNull(ruleCode, "ruleCode must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(network, "network must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(headingPath, "headingPath must not be null");
        Objects.requireNonNull(rawContent, "rawContent must not be null");
    }
}
