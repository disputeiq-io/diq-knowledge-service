package io.disputeiq.knowledge.api;

/** Request payload for parallel hybrid RAG search across accessible rulebook and tenant docs. */
public record KnowledgeSearchRequest(String query, String reasonCode, Integer topK) {}
