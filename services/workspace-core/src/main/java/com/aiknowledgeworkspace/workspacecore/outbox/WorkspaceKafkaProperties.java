package com.aiknowledgeworkspace.workspacecore.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.kafka")
public class WorkspaceKafkaProperties {

    private boolean enabled = false;
    private String bootstrapServers = "localhost:9092";
    private String processingRequestedTopic = "asset.processing.requested.v1";
    private Duration sendTimeout = Duration.ofSeconds(10);
    private boolean loggingPlaceholderEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getProcessingRequestedTopic() {
        return processingRequestedTopic;
    }

    public void setProcessingRequestedTopic(String processingRequestedTopic) {
        this.processingRequestedTopic = processingRequestedTopic;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public boolean isLoggingPlaceholderEnabled() {
        return loggingPlaceholderEnabled;
    }

    public void setLoggingPlaceholderEnabled(boolean loggingPlaceholderEnabled) {
        this.loggingPlaceholderEnabled = loggingPlaceholderEnabled;
    }
}
