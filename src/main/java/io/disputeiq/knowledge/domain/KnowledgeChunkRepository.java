package io.disputeiq.knowledge.domain;

import io.disputeiq.platform.tenancy.TenantId;
import java.util.List;
import java.util.Optional;

/** Persistence port for tenant-isolated and GLOBAL knowledge chunks. */
public interface KnowledgeChunkRepository {

    KnowledgeChunk saveOrUpdate(KnowledgeChunk chunk);

    Optional<KnowledgeChunk> findByTenantAndRuleCode(TenantId tenantId, String ruleCode);

    List<KnowledgeChunk> findAccessibleByTenant(TenantId tenantId, String reasonCode);
}
