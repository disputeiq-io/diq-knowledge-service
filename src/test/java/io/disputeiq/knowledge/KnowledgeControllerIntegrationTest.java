package io.disputeiq.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.disputeiq.knowledge.api.IngestKnowledgeRequest;
import io.disputeiq.knowledge.api.KnowledgeChunkResponse;
import io.disputeiq.knowledge.api.KnowledgeListResponse;
import io.disputeiq.knowledge.api.KnowledgeSearchRequest;
import io.disputeiq.knowledge.domain.SourceType;
import io.disputeiq.platform.tenancy.TenantId;
import io.disputeiq.platform.web.CorrelationHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KnowledgeControllerIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcClient jdbcClient;

    @Test
    void searchesGlobalRulebookWithRrfMasksPiiOnIngestAndIsolatesTenantPlaybooks() {
        TenantId alpha = TenantId.random();
        TenantId beta = TenantId.random();

        // 1. Default seeded GLOBAL rulebook is readable by any valid tenant.
        ResponseEntity<KnowledgeListResponse> globalChunks =
                restTemplate.exchange(
                        "/api/v1/knowledge/chunks",
                        HttpMethod.GET,
                        withTenant(alpha, null),
                        KnowledgeListResponse.class);
        assertThat(globalChunks.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(globalChunks.getBody()).isNotNull();
        assertThat(globalChunks.getBody().items()).hasSizeGreaterThanOrEqualTo(5);

        // 2. Hybrid RRF search ranks 10.4 CE 3.0 first for device/IP fraud query.
        ResponseEntity<KnowledgeListResponse> ceSearch =
                restTemplate.exchange(
                        "/api/v1/knowledge/search",
                        HttpMethod.POST,
                        withTenant(
                                alpha,
                                new KnowledgeSearchRequest(
                                        "Compelling Evidence 3.0 two prior undisputed device fingerprint IP address",
                                        null,
                                        5)),
                        KnowledgeListResponse.class);
        assertThat(ceSearch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ceSearch.getBody()).isNotNull();
        assertThat(ceSearch.getBody().items().get(0).ruleCode()).isEqualTo("RULE-VISA-10.4-CE30");
        assertThat(ceSearch.getBody().items().get(0).rrfScore()).isGreaterThan(0.0d);

        // 3. Hybrid RRF search ranks 13.1 POD first for signed delivery query.
        ResponseEntity<KnowledgeListResponse> podSearch =
                restTemplate.exchange(
                        "/api/v1/knowledge/search",
                        HttpMethod.POST,
                        withTenant(
                                alpha,
                                new KnowledgeSearchRequest(
                                        "carrier Proof of Delivery AVS billing address recipient signature",
                                        "13.1-MERCHANDISE-NOT-RECEIVED",
                                        3)),
                        KnowledgeListResponse.class);
        assertThat(podSearch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(podSearch.getBody()).isNotNull();
        assertThat(podSearch.getBody().items()).hasSize(1);
        assertThat(podSearch.getBody().items().get(0).ruleCode()).isEqualTo("RULE-VISA-13.1-POD");

        // 4. Tenant Alpha ingests private playbook containing PAN + email -> masked before
        // DB/outbox.
        IngestKnowledgeRequest ingestReq =
                new IngestKnowledgeRequest(
                        "PLAYBOOK-ALPHA-CUSTOM",
                        "Alpha High-Value SaaS Renewal Playbook",
                        "CARDNET_SYNTH",
                        "10.4-CARD-ABSENT-FRAUD",
                        SourceType.TENANT_PLAYBOOK,
                        "Tenant Playbook > Alpha > SaaS",
                        "Attach SAML assertion for test card 4111-1111-1111-1111 and security@alpha-merchant.example.");
        ResponseEntity<KnowledgeChunkResponse> created =
                restTemplate.exchange(
                        "/api/v1/knowledge/sources",
                        HttpMethod.POST,
                        withTenant(alpha, ingestReq),
                        KnowledgeChunkResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().content())
                .contains("****-****-****-1111")
                .contains("[REDACTED_EMAIL]")
                .doesNotContain("4111-1111-1111-1111")
                .doesNotContain("security@alpha-merchant.example");

        // Idempotent repeat of identical rule returns existing chunk; updated content updates row.
        ResponseEntity<KnowledgeChunkResponse> sameAgain =
                restTemplate.exchange(
                        "/api/v1/knowledge/sources",
                        HttpMethod.POST,
                        withTenant(alpha, ingestReq),
                        KnowledgeChunkResponse.class);
        assertThat(sameAgain.getBody()).isNotNull();
        assertThat(sameAgain.getBody().id()).isEqualTo(created.getBody().id());

        IngestKnowledgeRequest updatedReq =
                new IngestKnowledgeRequest(
                        "PLAYBOOK-ALPHA-CUSTOM",
                        "Alpha High-Value SaaS Renewal Playbook v2",
                        "CARDNET_SYNTH",
                        "10.4-CARD-ABSENT-FRAUD",
                        SourceType.TENANT_PLAYBOOK,
                        "Tenant Playbook > Alpha > SaaS",
                        "Attach SAML assertion, OAuth token log, and CE 3.0 fingerprint.");
        ResponseEntity<KnowledgeChunkResponse> updated =
                restTemplate.exchange(
                        "/api/v1/knowledge/sources",
                        HttpMethod.POST,
                        withTenant(alpha, updatedReq),
                        KnowledgeChunkResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().title())
                .isEqualTo("Alpha High-Value SaaS Renewal Playbook v2");

        Integer outboxCount =
                jdbcClient
                        .sql(
                                "SELECT COUNT(*) FROM outbox_event WHERE tenant_id = :tenantId AND topic = 'knowledge.source.ingested.v1'")
                        .param("tenantId", alpha.value())
                        .query(Integer.class)
                        .single();
        assertThat(outboxCount).isEqualTo(2);

        // 5. Tenant Beta cannot see Tenant Alpha's private playbook in list or search.
        ResponseEntity<KnowledgeListResponse> alphaFiltered =
                restTemplate.exchange(
                        "/api/v1/knowledge/chunks?reasonCode=10.4-CARD-ABSENT-FRAUD",
                        HttpMethod.GET,
                        withTenant(alpha, null),
                        KnowledgeListResponse.class);
        assertThat(alphaFiltered.getBody()).isNotNull();
        assertThat(alphaFiltered.getBody().items())
                .extracting(KnowledgeChunkResponse::ruleCode)
                .contains("PLAYBOOK-ALPHA-CUSTOM");

        ResponseEntity<KnowledgeListResponse> betaFiltered =
                restTemplate.exchange(
                        "/api/v1/knowledge/chunks?reasonCode=10.4-CARD-ABSENT-FRAUD",
                        HttpMethod.GET,
                        withTenant(beta, null),
                        KnowledgeListResponse.class);
        assertThat(betaFiltered.getBody()).isNotNull();
        assertThat(betaFiltered.getBody().items())
                .extracting(KnowledgeChunkResponse::ruleCode)
                .doesNotContain("PLAYBOOK-ALPHA-CUSTOM")
                .contains("RULE-VISA-10.4-CE30");
    }

    @Test
    void rejectsMissingTenantAndInvalidIngestPayloads() {
        ResponseEntity<ProblemDetail> missingTenant =
                restTemplate.getForEntity("/api/v1/knowledge/chunks", ProblemDetail.class);
        assertThat(missingTenant.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        HttpHeaders badHeaders = new HttpHeaders();
        badHeaders.set(CorrelationHeaders.TENANT_ID, "not-a-uuid");
        ResponseEntity<ProblemDetail> badTenant =
                restTemplate.exchange(
                        "/api/v1/knowledge/chunks",
                        HttpMethod.GET,
                        new HttpEntity<>(badHeaders),
                        ProblemDetail.class);
        assertThat(badTenant.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        TenantId tenant = TenantId.random();
        ResponseEntity<ProblemDetail> invalidBody =
                restTemplate.exchange(
                        "/api/v1/knowledge/sources",
                        HttpMethod.POST,
                        withTenant(
                                tenant,
                                new IngestKnowledgeRequest(
                                        " ", " ", " ", " ", SourceType.TENANT_PLAYBOOK, " ", " ")),
                        ProblemDetail.class);
        assertThat(invalidBody.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static <T> HttpEntity<T> withTenant(TenantId tenantId, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CorrelationHeaders.TENANT_ID, tenantId.toString());
        return new HttpEntity<>(body, headers);
    }
}
