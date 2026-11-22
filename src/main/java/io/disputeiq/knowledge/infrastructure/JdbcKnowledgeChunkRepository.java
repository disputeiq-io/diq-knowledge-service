package io.disputeiq.knowledge.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.disputeiq.events.v1.KnowledgeSourceIngestedEvent;
import io.disputeiq.events.v1.TopicNames;
import io.disputeiq.knowledge.domain.KnowledgeChunk;
import io.disputeiq.knowledge.domain.KnowledgeChunkRepository;
import io.disputeiq.knowledge.domain.SourceType;
import io.disputeiq.platform.ai.DeterministicEmbeddingGenerator;
import io.disputeiq.platform.tenancy.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC repository storing PII-scrubbed policy chunks (tenant-private + GLOBAL rulebook) and atomic
 * {@code outbox_event} notifications.
 */
@Repository
public class JdbcKnowledgeChunkRepository implements KnowledgeChunkRepository {

    private final JdbcClient jdbcClient;
    private final TenantRlsContext rlsContext;
    private final ObjectMapper objectMapper;

    public JdbcKnowledgeChunkRepository(
            JdbcClient jdbcClient, TenantRlsContext rlsContext, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.rlsContext = rlsContext;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public KnowledgeChunk saveOrUpdate(KnowledgeChunk chunk) {
        rlsContext.apply(jdbcClient, chunk.tenantId());
        String embeddingCsv = DeterministicEmbeddingGenerator.toCsv(chunk.embedding());
        OffsetDateTime createdAt = OffsetDateTime.ofInstant(chunk.createdAt(), ZoneOffset.UTC);

        Optional<KnowledgeChunk> existing =
                findByTenantAndRuleCode(chunk.tenantId(), chunk.ruleCode());
        if (existing.isPresent()) {
            jdbcClient
                    .sql(
                            """
                            UPDATE knowledge_chunk
                               SET title = :title,
                                   network = :network,
                                   reason_code = :reasonCode,
                                   source_type = :sourceType,
                                   heading_path = :headingPath,
                                   content = :content,
                                   content_sha256 = :contentSha256,
                                   embedding_csv = :embeddingCsv,
                                   created_at = :createdAt
                             WHERE tenant_id = :tenantId AND rule_code = :ruleCode
                            """)
                    .param("title", chunk.title())
                    .param("network", chunk.network())
                    .param("reasonCode", chunk.reasonCode())
                    .param("sourceType", chunk.sourceType().name())
                    .param("headingPath", chunk.headingPath())
                    .param("content", chunk.content())
                    .param("contentSha256", chunk.contentSha256())
                    .param("embeddingCsv", embeddingCsv)
                    .param("createdAt", createdAt)
                    .param("tenantId", chunk.tenantId().value())
                    .param("ruleCode", chunk.ruleCode())
                    .update();
        } else {
            jdbcClient
                    .sql(
                            """
                            INSERT INTO knowledge_chunk (
                                id, tenant_id, rule_code, title, network, reason_code,
                                source_type, heading_path, content, content_sha256,
                                embedding_csv, created_at
                            ) VALUES (
                                :id, :tenantId, :ruleCode, :title, :network, :reasonCode,
                                :sourceType, :headingPath, :content, :contentSha256,
                                :embeddingCsv, :createdAt
                            )
                            """)
                    .param("id", chunk.id())
                    .param("tenantId", chunk.tenantId().value())
                    .param("ruleCode", chunk.ruleCode())
                    .param("title", chunk.title())
                    .param("network", chunk.network())
                    .param("reasonCode", chunk.reasonCode())
                    .param("sourceType", chunk.sourceType().name())
                    .param("headingPath", chunk.headingPath())
                    .param("content", chunk.content())
                    .param("contentSha256", chunk.contentSha256())
                    .param("embeddingCsv", embeddingCsv)
                    .param("createdAt", createdAt)
                    .update();
        }

        UUID eventId = UUID.randomUUID();
        KnowledgeSourceIngestedEvent outboxPayload =
                new KnowledgeSourceIngestedEvent(
                        eventId.toString(),
                        chunk.tenantId().toString(),
                        chunk.id().toString(),
                        chunk.ruleCode(),
                        chunk.reasonCode(),
                        chunk.sourceType().name(),
                        chunk.contentSha256(),
                        chunk.createdAt().toString());
        jdbcClient
                .sql(
                        """
                        INSERT INTO outbox_event (
                            id, tenant_id, aggregate_type, aggregate_id,
                            event_type, topic, payload_json, status, created_at
                        ) VALUES (
                            :id, :tenantId, :aggregateType, :aggregateId,
                            :eventType, :topic, :payloadJson, :status, :createdAt
                        )
                        """)
                .param("id", eventId)
                .param("tenantId", chunk.tenantId().value())
                .param("aggregateType", "KNOWLEDGE_CHUNK")
                .param("aggregateId", chunk.id().toString())
                .param("eventType", TopicNames.KNOWLEDGE_SOURCE_INGESTED_V1)
                .param("topic", TopicNames.KNOWLEDGE_SOURCE_INGESTED_V1)
                .param("payloadJson", toJson(outboxPayload))
                .param("status", "PENDING")
                .param("createdAt", createdAt)
                .update();
        return chunk;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KnowledgeChunk> findByTenantAndRuleCode(TenantId tenantId, String ruleCode) {
        rlsContext.apply(jdbcClient, tenantId);
        return jdbcClient
                .sql(
                        """
                        SELECT id, tenant_id, rule_code, title, network, reason_code,
                               source_type, heading_path, content, content_sha256,
                               embedding_csv, created_at
                          FROM knowledge_chunk
                         WHERE tenant_id = :tenantId AND rule_code = :ruleCode
                        """)
                .param("tenantId", tenantId.value())
                .param("ruleCode", ruleCode)
                .query(JdbcKnowledgeChunkRepository::mapRow)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeChunk> findAccessibleByTenant(TenantId tenantId, String reasonCode) {
        rlsContext.apply(jdbcClient, tenantId);
        if (reasonCode == null || reasonCode.isBlank()) {
            return jdbcClient
                    .sql(
                            """
                            SELECT id, tenant_id, rule_code, title, network, reason_code,
                                   source_type, heading_path, content, content_sha256,
                                   embedding_csv, created_at
                              FROM knowledge_chunk
                             WHERE tenant_id = :tenantId OR tenant_id = :globalTenantId
                             ORDER BY created_at DESC
                            """)
                    .param("tenantId", tenantId.value())
                    .param("globalTenantId", TenantId.GLOBAL.value())
                    .query(JdbcKnowledgeChunkRepository::mapRow)
                    .list();
        }
        return jdbcClient
                .sql(
                        """
                        SELECT id, tenant_id, rule_code, title, network, reason_code,
                               source_type, heading_path, content, content_sha256,
                               embedding_csv, created_at
                          FROM knowledge_chunk
                         WHERE (tenant_id = :tenantId OR tenant_id = :globalTenantId)
                           AND reason_code = :reasonCode
                         ORDER BY created_at DESC
                        """)
                .param("tenantId", tenantId.value())
                .param("globalTenantId", TenantId.GLOBAL.value())
                .param("reasonCode", reasonCode)
                .query(JdbcKnowledgeChunkRepository::mapRow)
                .list();
    }

    private static KnowledgeChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeChunk(
                rs.getObject("id", UUID.class),
                new TenantId(rs.getObject("tenant_id", UUID.class)),
                rs.getString("rule_code"),
                rs.getString("title"),
                rs.getString("network"),
                rs.getString("reason_code"),
                SourceType.valueOf(rs.getString("source_type")),
                rs.getString("heading_path"),
                rs.getString("content"),
                rs.getString("content_sha256"),
                DeterministicEmbeddingGenerator.fromCsv(rs.getString("embedding_csv")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize knowledge outbox payload", ex);
        }
    }
}
