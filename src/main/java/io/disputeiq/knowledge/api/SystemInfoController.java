package io.disputeiq.knowledge.api;

import io.disputeiq.knowledge.application.ServiceMetadataUseCase;
import io.disputeiq.knowledge.domain.ServiceDescriptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes service metadata and virtual-thread verification telemetry. */
@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    private final ServiceMetadataUseCase metadataUseCase;

    public SystemInfoController(ServiceMetadataUseCase metadataUseCase) {
        this.metadataUseCase = metadataUseCase;
    }

    @GetMapping("/info")
    public ServiceDescriptor info() {
        return metadataUseCase.describeCurrentExecution();
    }
}
