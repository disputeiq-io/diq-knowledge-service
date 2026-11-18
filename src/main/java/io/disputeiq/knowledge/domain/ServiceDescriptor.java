package io.disputeiq.knowledge.domain;

import java.time.Instant;
import java.util.Objects;

/** Pure domain value object describing service runtime metadata without framework coupling. */
public record ServiceDescriptor(
        String serviceName,
        String environment,
        boolean virtualThreadsActive,
        String tenantId,
        Instant observedAt) {

    public ServiceDescriptor {
        Objects.requireNonNull(serviceName, "serviceName must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }
}
