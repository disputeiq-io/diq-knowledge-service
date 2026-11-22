package io.disputeiq.knowledge.api;

import io.disputeiq.knowledge.domain.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request payload to ingest and embed a tenant-scoped policy or precedent chunk. */
public record IngestKnowledgeRequest(
        @NotBlank String ruleCode,
        @NotBlank String title,
        @NotBlank String network,
        @NotBlank String reasonCode,
        @NotNull SourceType sourceType,
        @NotBlank String headingPath,
        @NotBlank String content) {}
