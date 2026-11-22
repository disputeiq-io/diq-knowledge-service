package io.disputeiq.knowledge.api;

import java.util.List;

/** Collection envelope wrapping knowledge chunks or hybrid search results. */
public record KnowledgeListResponse(List<KnowledgeChunkResponse> items) {}
