package com.aiknowledgeworkspace.workspacecore.outbox.application.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox.recovery")
public class OutboxRecoveryProperties {

    private boolean enabled = false;
    private Duration interval = Duration.ofSeconds(30);
    private Duration cooldown = Duration.ofSeconds(60);
    private int batchSize = 50;
    private int maxCycles = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        requirePositive(interval, "outbox.recovery.interval");
        this.interval = interval;
    }

    public Duration getCooldown() {
        return cooldown;
    }

    public void setCooldown(Duration cooldown) {
        requirePositive(cooldown, "outbox.recovery.cooldown");
        this.cooldown = cooldown;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("outbox.recovery.batch-size must be between 1 and 1000");
        }
        this.batchSize = batchSize;
    }

    public int getMaxCycles() {
        return maxCycles;
    }

    public void setMaxCycles(int maxCycles) {
        if (maxCycles < 1 || maxCycles > 100) {
            throw new IllegalArgumentException("outbox.recovery.max-cycles must be between 1 and 100");
        }
        this.maxCycles = maxCycles;
    }

    private void requirePositive(Duration value, String propertyName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
    }
}
