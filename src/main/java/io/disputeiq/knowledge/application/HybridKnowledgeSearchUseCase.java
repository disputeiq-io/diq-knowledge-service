package io.disputeiq.knowledge.application;

import io.disputeiq.knowledge.domain.KnowledgeChunk;
import io.disputeiq.knowledge.domain.KnowledgeChunkRepository;
import io.disputeiq.knowledge.domain.ScoredKnowledgeChunk;
import io.disputeiq.knowledge.domain.SourceType;
import io.disputeiq.platform.ai.DeterministicEmbeddingGenerator;
import io.disputeiq.platform.ai.ReciprocalRankFusion;
import io.disputeiq.platform.privacy.PiiRedactor;
import io.disputeiq.platform.tenancy.TenantAware;
import io.disputeiq.platform.tenancy.TenantContext;
import io.disputeiq.platform.tenancy.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Application service orchestrating PII-safe policy ingestion and parallel virtual-thread hybrid
 * RAG retrieval (dense cosine + sparse lexical BM25 fused via Reciprocal Rank Fusion).
 */
public class HybridKnowledgeSearchUseCase {

    private final KnowledgeChunkRepository repository;
    private final PiiRedactor piiRedactor;
    private final DeterministicEmbeddingGenerator embeddingGenerator;
    private final Clock clock;

    public HybridKnowledgeSearchUseCase(
            KnowledgeChunkRepository repository,
            PiiRedactor piiRedactor,
            DeterministicEmbeddingGenerator embeddingGenerator,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.piiRedactor = Objects.requireNonNull(piiRedactor, "piiRedactor must not be null");
        this.embeddingGenerator =
                Objects.requireNonNull(embeddingGenerator, "embeddingGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public KnowledgeChunk ingest(IngestKnowledgeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        TenantId targetTenant =
                command.globalRulebook() ? TenantId.GLOBAL : TenantContext.require();
        String normalizedCode = command.ruleCode().strip();
        String redactedContent = piiRedactor.redact(command.rawContent()).redactedText().strip();
        String sha256 = sha256Hex(normalizedCode + "|" + redactedContent);

        Optional<KnowledgeChunk> existing =
                repository.findByTenantAndRuleCode(targetTenant, normalizedCode);
        if (existing.isPresent() && existing.get().contentSha256().equals(sha256)) {
            return existing.get();
        }

        UUID chunkId = existing.map(KnowledgeChunk::id).orElseGet(UUID::randomUUID);
        double[] embedding =
                embeddingGenerator.embed(
                        normalizedCode
                                + " "
                                + command.title()
                                + " "
                                + command.reasonCode()
                                + " "
                                + redactedContent);
        KnowledgeChunk chunk =
                new KnowledgeChunk(
                        chunkId,
                        targetTenant,
                        normalizedCode,
                        command.title().strip(),
                        command.network().strip(),
                        command.reasonCode().strip(),
                        command.sourceType(),
                        command.headingPath().strip(),
                        redactedContent,
                        sha256,
                        embedding,
                        clock.instant());
        return repository.saveOrUpdate(chunk);
    }

    public List<KnowledgeChunk> listAccessible(String reasonCode) {
        TenantId tenantId = TenantContext.require();
        return repository.findAccessibleByTenant(tenantId, normalizeFilter(reasonCode));
    }

    public List<ScoredKnowledgeChunk> hybridSearch(String query, String reasonCode, int topK) {
        TenantId tenantId = TenantContext.require();
        int effectiveTopK = topK <= 0 ? 5 : Math.min(topK, 25);
        String effectiveQuery =
                (query == null || query.isBlank()) ? "compelling evidence rulebook" : query.strip();
        List<KnowledgeChunk> candidates =
                repository.findAccessibleByTenant(tenantId, normalizeFilter(reasonCode));
        if (candidates.isEmpty()) {
            return List.of();
        }

        double[] queryVector = embeddingGenerator.embed(effectiveQuery);
        Map<String, Double> cosineScores = new HashMap<>();
        Map<String, Double> lexicalScores = new HashMap<>();
        for (KnowledgeChunk chunk : candidates) {
            String fullText =
                    chunk.ruleCode()
                            + " "
                            + chunk.title()
                            + " "
                            + chunk.reasonCode()
                            + " "
                            + chunk.content();
            cosineScores.put(
                    chunk.ruleCode(),
                    DeterministicEmbeddingGenerator.cosineSimilarity(
                            queryVector, chunk.embedding()));
            lexicalScores.put(
                    chunk.ruleCode(),
                    DeterministicEmbeddingGenerator.lexicalOverlapScore(effectiveQuery, fullText));
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<KnowledgeChunk>> denseFuture =
                    executor.submit(
                            TenantAware.wrap(
                                    tenantId, () -> rankByScore(candidates, cosineScores)));
            Future<List<KnowledgeChunk>> sparseFuture =
                    executor.submit(
                            TenantAware.wrap(
                                    tenantId, () -> rankByScore(candidates, lexicalScores)));

            List<ReciprocalRankFusion.RankedItem<KnowledgeChunk>> fused =
                    ReciprocalRankFusion.fuse(
                            denseFuture.get(),
                            sparseFuture.get(),
                            KnowledgeChunk::ruleCode,
                            ReciprocalRankFusion.DEFAULT_K,
                            effectiveTopK);

            return fused.stream()
                    .map(
                            ranked ->
                                    new ScoredKnowledgeChunk(
                                            ranked.item(),
                                            ranked.rrfScore(),
                                            cosineScores.getOrDefault(ranked.id(), 0.0d),
                                            lexicalScores.getOrDefault(ranked.id(), 0.0d),
                                            ranked.denseRank(),
                                            ranked.sparseRank()))
                    .toList();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during hybrid RAG retrieval", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Parallel hybrid RAG retrieval failed", ex);
        }
    }

    public void seedDefaultRulebookIfMissing() {
        List<IngestKnowledgeCommand> defaults =
                List.of(
                        new IngestKnowledgeCommand(
                                "RULE-VISA-10.4-CE30",
                                "Reason Code 10.4 — Compelling Evidence 3.0 (CE 3.0) Remedy",
                                "CARDNET_SYNTH",
                                "10.4-CARD-ABSENT-FRAUD",
                                SourceType.NETWORK_RULEBOOK,
                                "Rulebook > Fraud > 10.4 Card-Absent Environment > Section 4.2 CE 3.0",
                                "To shift liability under Reason Code 10.4 (Other Fraud — Card-Absent Environment) using Compelling Evidence 3.0, the merchant must provide at least two prior undisputed transactions settled more than 60-120 days before the dispute date where at least two core data elements (IP address, Device ID/Fingerprint, shipping address, or account login ID) match the disputed transaction.",
                                true),
                        new IngestKnowledgeCommand(
                                "RULE-VISA-13.1-POD",
                                "Reason Code 13.1 — Merchandise/Services Not Received Representment",
                                "CARDNET_SYNTH",
                                "13.1-MERCHANDISE-NOT-RECEIVED",
                                SourceType.NETWORK_RULEBOOK,
                                "Rulebook > Consumer Disputes > 13.1 Merchandise Not Received > Section 2.1",
                                "For tangible goods under Reason Code 13.1, representment requires carrier-verified Proof of Delivery (POD) showing delivery to the cardholder AVS-verified billing/shipping address and a recipient signature or geo-tagged delivery confirmation dated prior to the dispute filing timestamp.",
                                true),
                        new IngestKnowledgeCommand(
                                "RULE-VISA-12.6-DUP",
                                "Reason Code 12.6 — Duplicate Processing & Remedies",
                                "CARDNET_SYNTH",
                                "12.6-DUPLICATE-PROCESSING",
                                SourceType.NETWORK_RULEBOOK,
                                "Rulebook > Processing Errors > 12.6 Duplicate Processing > Section 1.4",
                                "If two transactions share the same card number, merchant, and amount within the same settlement batch, the acquirer must either provide two distinct itemized invoices/order IDs proving separate purchases or accept liability and issue an immediate credit reversal.",
                                true),
                        new IngestKnowledgeCommand(
                                "RULE-VISA-13.7-CANCEL",
                                "Reason Code 13.7 — Cancelled Merchandise/Services & Subscription Disclosure",
                                "CARDNET_SYNTH",
                                "13.7-CANCELLED-RECURRING",
                                SourceType.NETWORK_RULEBOOK,
                                "Rulebook > Consumer Disputes > 13.7 Cancelled Recurring > Section 3.3",
                                "To rebut a Reason Code 13.7 dispute, the merchant must supply evidence that the cardholder agreed to the cancellation window at checkout, received a renewal reminder at least 7 days before billing, and actively used the service after the disputed charge date.",
                                true),
                        new IngestKnowledgeCommand(
                                "RULE-VISA-13.6-CREDIT",
                                "Reason Code 13.6 — Credit Not Processed Return Policy Rules",
                                "CARDNET_SYNTH",
                                "13.6-CREDIT-NOT-PROCESSED",
                                SourceType.NETWORK_RULEBOOK,
                                "Rulebook > Consumer Disputes > 13.6 Credit Not Processed > Section 5.1",
                                "When a cardholder presents a return receipt or RMA acknowledgment under Reason Code 13.6, the merchant must either prove a credit ARN was already settled to the cardholder account or show clearly disclosed non-refundable terms accepted at checkout.",
                                true));
        TenantContext.runWith(
                TenantId.GLOBAL,
                () -> {
                    for (IngestKnowledgeCommand cmd : defaults) {
                        ingest(cmd);
                    }
                });
    }

    private static List<KnowledgeChunk> rankByScore(
            List<KnowledgeChunk> candidates, Map<String, Double> scores) {
        return candidates.stream()
                .sorted(
                        Comparator.comparingDouble(
                                        (KnowledgeChunk c) ->
                                                scores.getOrDefault(c.ruleCode(), 0.0d))
                                .reversed()
                                .thenComparing(KnowledgeChunk::ruleCode))
                .toList();
    }

    private static String normalizeFilter(String reasonCode) {
        return (reasonCode == null || reasonCode.isBlank()) ? null : reasonCode.strip();
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
