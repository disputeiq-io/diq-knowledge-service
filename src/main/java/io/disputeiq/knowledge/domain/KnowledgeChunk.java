package io.disputeiq.knowledge.domain;

import io.disputeiq.platform.tenancy.TenantId;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Immutable domain entity representing a PII-scrubbed, embedded policy or rulebook chunk. */
public record KnowledgeChunk(
        UUID id,
        TenantId tenantId,
        String ruleCode,
        String title,
        String network,
        String reasonCode,
        SourceType sourceType,
        String headingPath,
        String content,
        String contentSha256,
        double[] embedding,
        Instant createdAt) {

    public KnowledgeChunk {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(ruleCode, "ruleCode must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(network, "network must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(headingPath, "headingPath must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(contentSha256, "contentSha256 must not be null");
        Objects.requireNonNull(embedding, "embedding must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        embedding = Arrays.copyOf(embedding, embedding.length);
    }
}
