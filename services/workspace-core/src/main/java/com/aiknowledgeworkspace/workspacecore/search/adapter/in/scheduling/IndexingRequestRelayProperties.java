package com.aiknowledgeworkspace.workspacecore.search.adapter.in.scheduling;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.search.indexing-relay")
public class IndexingRequestRelayProperties {

    private boolean enabled = false;
    private Duration fixedDelay = Duration.ofSeconds(10);
    private int batchSize = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getFixedDelay() {
        return fixedDelay;
    }

    public void setFixedDelay(Duration fixedDelay) {
        if (fixedDelay == null) {
            throw new IllegalArgumentException("workspace.search.indexing-relay.fixed-delay is required");
        }
        if (fixedDelay.isZero() || fixedDelay.isNegative()) {
            throw new IllegalArgumentException("workspace.search.indexing-relay.fixed-delay must be positive");
        }
        this.fixedDelay = fixedDelay;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("workspace.search.indexing-relay.batch-size must be at least 1");
        }
        this.batchSize = batchSize;
    }
}
