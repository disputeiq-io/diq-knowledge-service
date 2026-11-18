package io.disputeiq.knowledge.application;

import io.disputeiq.knowledge.domain.ServiceDescriptor;
import io.disputeiq.platform.tenancy.TenantContext;
import io.disputeiq.platform.tenancy.TenantId;
import java.time.Clock;

/** Application use case assembling service diagnostics for readiness and smoke checks. */
public class ServiceMetadataUseCase {

    private final String serviceName;
    private final String environment;
    private final Clock clock;

    public ServiceMetadataUseCase(String serviceName, String environment, Clock clock) {
        this.serviceName = serviceName;
        this.environment = environment;
        this.clock = clock;
    }

    public ServiceDescriptor describeCurrentExecution() {
        String tenant = TenantContext.current().map(TenantId::toString).orElse("unbound");
        return new ServiceDescriptor(
                serviceName,
                environment,
                Thread.currentThread().isVirtual(),
                tenant,
                clock.instant());
    }
}
