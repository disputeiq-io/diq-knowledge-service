package io.disputeiq.knowledge.api;

import io.disputeiq.knowledge.domain.KnowledgeChunk;
import io.disputeiq.knowledge.domain.ScoredKnowledgeChunk;
import io.disputeiq.knowledge.domain.SourceType;
import java.util.UUID;

/** API representation of an indexed or retrieved knowledge chunk with optional RRF scores. */
public record KnowledgeChunkResponse(
        UUID id,
        UUID tenantId,
        String ruleCode,
        String title,
        String network,
        String reasonCode,
        SourceType sourceType,
        String headingPath,
        String content,
        Double rrfScore,
        Double cosineScore,
        Double lexicalScore) {

    public static KnowledgeChunkResponse fromDomain(KnowledgeChunk chunk) {
        return new KnowledgeChunkResponse(
                chunk.id(),
                chunk.tenantId().value(),
                chunk.ruleCode(),
                chunk.title(),
                chunk.network(),
                chunk.reasonCode(),
                chunk.sourceType(),
                chunk.headingPath(),
                chunk.content(),
                null,
                null,
                null);
    }

    public static KnowledgeChunkResponse fromScored(ScoredKnowledgeChunk scored) {
        KnowledgeChunk chunk = scored.chunk();
        return new KnowledgeChunkResponse(
                chunk.id(),
                chunk.tenantId().value(),
                chunk.ruleCode(),
                chunk.title(),
                chunk.network(),
                chunk.reasonCode(),
                chunk.sourceType(),
                chunk.headingPath(),
                chunk.content(),
                scored.rrfScore(),
                scored.cosineScore(),
                scored.lexicalScore());
    }
}
