package com.aiknowledgeworkspace.workspacecore.common.health.adapter.in.web;

import java.util.Map;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.SimpleHttpCodeStatusMapper;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Compatibility alias kept for the smoke tooling that already reads {@code status} and
 * {@code service} from {@code GET /health}. The answer is no longer a static {@code UP}: it is
 * the readiness verdict, so a caller that gets {@code 200} may send product traffic and a
 * caller that gets {@code 503} must not. Liveness and per-capability detail live on the
 * standard actuator surface.
 */
@RestController
public class HealthController {

    private static final String SERVICE_NAME = "workspace-core";
    private static final String READINESS_GROUP = "readiness";

    private final HealthEndpoint healthEndpoint;
    private final SimpleHttpCodeStatusMapper statusMapper = new SimpleHttpCodeStatusMapper();

    HealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Status status = readinessStatus();
        return ResponseEntity
                .status(statusMapper.getStatusCode(status))
                .body(Map.of(
                        "status", status.getCode(),
                        "service", SERVICE_NAME
                ));
    }

    private Status readinessStatus() {
        HealthComponent readiness = healthEndpoint.healthForPath(READINESS_GROUP);
        if (readiness != null) {
            return readiness.getStatus();
        }
        // A missing readiness group is a configuration regression; the aggregate is the
        // stricter honest fallback rather than a hardcoded UP.
        return healthEndpoint.health().getStatus();
    }
}
