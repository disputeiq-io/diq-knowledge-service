package io.disputeiq.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.disputeiq.knowledge.application.ServiceMetadataUseCase;
import io.disputeiq.knowledge.domain.ServiceDescriptor;
import io.disputeiq.platform.tenancy.TenantContext;
import io.disputeiq.platform.tenancy.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SystemInfoControllerTest {

    @Autowired private TestRestTemplate restTemplate;

    @Autowired private ServiceMetadataUseCase metadataUseCase;

    @Test
    void systemInfoEndpointRunsOnVirtualThread() {
        ServiceDescriptor body =
                restTemplate.getForObject("/api/v1/system/info", ServiceDescriptor.class);

        assertThat(body).isNotNull();
        assertThat(body.serviceName()).isEqualTo("diq-knowledge-service");
        assertThat(body.environment()).isEqualTo("local");
        assertThat(body.virtualThreadsActive()).isTrue();
        assertThat(body.tenantId()).isEqualTo("unbound");
    }

    @Test
    void useCaseCapturesBoundTenantContext() {
        TenantId expected = TenantId.random();
        TenantContext.runWith(
                expected,
                () -> {
                    ServiceDescriptor descriptor = metadataUseCase.describeCurrentExecution();
                    assertThat(descriptor.tenantId()).isEqualTo(expected.toString());
                });
    }
}
