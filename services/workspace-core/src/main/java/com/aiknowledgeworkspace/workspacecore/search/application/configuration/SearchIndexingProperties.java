package com.aiknowledgeworkspace.workspacecore.search.application.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.search.indexing")
public class SearchIndexingProperties {

    private boolean autoRequestEnabled = false;
    /**
     * How long a job may hold an indexing claim before the worker that took it is presumed dead.
     * This is the single owner of that definition: recovery accepts a job past it and the stuck
     * gauge counts the same jobs, so detection and repair cannot drift apart. An attempt is bounded
     * by the Elasticsearch connect and read timeouts (seconds), so minutes is a wide margin.
     */
    private Duration staleAge = Duration.ofMinutes(5);
    private boolean recoveryEnabled = false;
    private Duration recoveryInterval = Duration.ofSeconds(60);
    private int recoveryBatchSize = 20;

    public boolean isAutoRequestEnabled() {
        return autoRequestEnabled;
    }

    public void setAutoRequestEnabled(boolean autoRequestEnabled) {
        this.autoRequestEnabled = autoRequestEnabled;
    }

    public Duration getStaleAge() {
        return staleAge;
    }

    public void setStaleAge(Duration staleAge) {
        requirePositive(staleAge, "workspace.search.indexing.stale-age");
        this.staleAge = staleAge;
    }

    public boolean isRecoveryEnabled() {
        return recoveryEnabled;
    }

    public void setRecoveryEnabled(boolean recoveryEnabled) {
        this.recoveryEnabled = recoveryEnabled;
    }

    public Duration getRecoveryInterval() {
        return recoveryInterval;
    }

    public void setRecoveryInterval(Duration recoveryInterval) {
        requirePositive(recoveryInterval, "workspace.search.indexing.recovery-interval");
        this.recoveryInterval = recoveryInterval;
    }

    public int getRecoveryBatchSize() {
        return recoveryBatchSize;
    }

    public void setRecoveryBatchSize(int recoveryBatchSize) {
        if (recoveryBatchSize < 1 || recoveryBatchSize > 1_000) {
            throw new IllegalArgumentException(
                    "workspace.search.indexing.recovery-batch-size must be between 1 and 1000");
        }
        this.recoveryBatchSize = recoveryBatchSize;
    }

    private void requirePositive(Duration value, String propertyName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
    }
}
