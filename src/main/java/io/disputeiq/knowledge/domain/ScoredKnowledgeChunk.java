package io.disputeiq.knowledge.domain;

import java.util.Objects;

/** Knowledge chunk paired with hybrid Reciprocal Rank Fusion, dense cosine, and lexical scores. */
public record ScoredKnowledgeChunk(
        KnowledgeChunk chunk,
        double rrfScore,
        double cosineScore,
        double lexicalScore,
        int denseRank,
        int sparseRank) {

    public ScoredKnowledgeChunk {
        Objects.requireNonNull(chunk, "chunk must not be null");
    }
}
