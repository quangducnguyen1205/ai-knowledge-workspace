package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Processing/assistant capability health. FastAPI is an internal processing dependency: when
 * it is down, transcription and assistant answers are unavailable, but core product reads
 * still work — so this indicator reports as a component of the aggregate health and is
 * deliberately excluded from the readiness group. The probe hits the processor's intentionally
 * unauthenticated {@code GET /health} through a dedicated anonymous client with a short read
 * timeout; any non-2xx or transport failure is DOWN.
 */
@Component("fastapiHealthIndicator")
class FastApiHealthIndicator implements HealthIndicator {

    private final RestClient fastApiHealthRestClient;

    FastApiHealthIndicator(@Qualifier("fastApiHealthRestClient") RestClient fastApiHealthRestClient) {
        this.fastApiHealthRestClient = fastApiHealthRestClient;
    }

    @Override
    public Health health() {
        try {
            fastApiHealthRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return Health.up().build();
        } catch (RuntimeException exception) {
            return Health.down().withDetail("reason", "UNREACHABLE").build();
        }
    }
}
