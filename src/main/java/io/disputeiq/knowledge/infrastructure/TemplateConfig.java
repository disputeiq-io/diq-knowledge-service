package io.disputeiq.knowledge.infrastructure;

import io.disputeiq.knowledge.application.ServiceMetadataUseCase;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Infrastructure wiring for deterministic clocks, typed configuration, and use cases. */
@Configuration
public class TemplateConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ServiceMetadataUseCase serviceMetadataUseCase(
            @Value("${spring.application.name}") String applicationName,
            ServiceProperties properties,
            Clock clock) {
        return new ServiceMetadataUseCase(applicationName, properties.environment(), clock);
    }

    @ConfigurationProperties(prefix = "diq.service")
    public record ServiceProperties(String environment) {

        public ServiceProperties {
            if (environment == null || environment.isBlank()) {
                environment = "local";
            }
        }
    }
}
