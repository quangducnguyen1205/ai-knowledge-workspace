package com.aiknowledgeworkspace.workspacecore.search.adapter.out.search;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Search capability health. Elasticsearch is a derived read model: when it is degraded the
 * search capability is degraded, but workspace, library, and authentication still work, so
 * this indicator reports as a component of the aggregate health and is deliberately excluded
 * from the readiness group. UP mirrors the infra healthcheck contract — {@code green} or
 * {@code yellow} from {@code _cluster/health}; {@code red} or unreachable is DOWN. The probe
 * reuses the adapter's client and its bounded connect/read timeouts and never touches an index.
 */
@Component("elasticsearchHealthIndicator")
class ElasticsearchHealthIndicator implements HealthIndicator {

    private final RestClient elasticsearchRestClient;

    ElasticsearchHealthIndicator(@Qualifier("elasticsearchRestClient") RestClient elasticsearchRestClient) {
        this.elasticsearchRestClient = elasticsearchRestClient;
    }

    @Override
    public Health health() {
        JsonNode clusterHealth;
        try {
            clusterHealth = elasticsearchRestClient.get()
                    .uri("/_cluster/health")
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException exception) {
            return Health.down().withDetail("reason", "UNREACHABLE").build();
        }

        String clusterStatus = clusterHealth == null ? "" : clusterHealth.path("status").asText("");
        if ("green".equals(clusterStatus) || "yellow".equals(clusterStatus)) {
            return Health.up().withDetail("clusterStatus", clusterStatus).build();
        }
        return Health.down()
                .withDetail("clusterStatus", clusterStatus.isEmpty() ? "unknown" : clusterStatus)
                .build();
    }
}
