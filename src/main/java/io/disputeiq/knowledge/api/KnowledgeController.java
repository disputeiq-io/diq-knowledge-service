package io.disputeiq.knowledge.api;

import io.disputeiq.knowledge.application.HybridKnowledgeSearchUseCase;
import io.disputeiq.knowledge.application.IngestKnowledgeCommand;
import io.disputeiq.knowledge.domain.KnowledgeChunk;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for tenant-scoped policy ingestion and hybrid RRF retrieval. */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final HybridKnowledgeSearchUseCase knowledgeUseCase;

    public KnowledgeController(HybridKnowledgeSearchUseCase knowledgeUseCase) {
        this.knowledgeUseCase = knowledgeUseCase;
    }

    @PostMapping("/sources")
    public ResponseEntity<KnowledgeChunkResponse> ingest(
            @Valid @RequestBody IngestKnowledgeRequest request) {
        KnowledgeChunk saved =
                knowledgeUseCase.ingest(
                        new IngestKnowledgeCommand(
                                request.ruleCode(),
                                request.title(),
                                request.network(),
                                request.reasonCode(),
                                request.sourceType(),
                                request.headingPath(),
                                request.content(),
                                false));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(KnowledgeChunkResponse.fromDomain(saved));
    }

    @PostMapping("/search")
    public KnowledgeListResponse search(@RequestBody(required = false) KnowledgeSearchRequest req) {
        String query = req == null ? null : req.query();
        String reasonCode = req == null ? null : req.reasonCode();
        int topK = (req == null || req.topK() == null) ? 6 : req.topK();
        List<KnowledgeChunkResponse> items =
                knowledgeUseCase.hybridSearch(query, reasonCode, topK).stream()
                        .map(KnowledgeChunkResponse::fromScored)
                        .toList();
        return new KnowledgeListResponse(items);
    }

    @GetMapping("/chunks")
    public KnowledgeListResponse listChunks(@RequestParam(required = false) String reasonCode) {
        List<KnowledgeChunkResponse> items =
                knowledgeUseCase.listAccessible(reasonCode).stream()
                        .map(KnowledgeChunkResponse::fromDomain)
                        .toList();
        return new KnowledgeListResponse(items);
    }
}
