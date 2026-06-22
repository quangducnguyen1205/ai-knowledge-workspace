package com.aiknowledgeworkspace.workspacecore.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.kafka")
public class WorkspaceKafkaProperties {

    private boolean enabled = false;
    private String bootstrapServers = "localhost:9092";
    private String processingRequestedTopic = "asset.processing.requested.v1";
    private String processingResultTopic = "asset.processing.result.v1";
    private String indexingRequestedTopic = "asset.indexing.requested.v1";
    private boolean processingResultListenerEnabled = false;
    private String processingResultConsumerGroup = "workspace-processing-result-v1";
    private String processingResultAutoOffsetReset = "latest";
    private boolean indexingListenerEnabled = false;
    private String indexingConsumerGroup = "workspace-search-indexer-v1";
    private String indexingAutoOffsetReset = "latest";
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

    public String getProcessingResultTopic() {
        return processingResultTopic;
    }

    public void setProcessingResultTopic(String processingResultTopic) {
        this.processingResultTopic = processingResultTopic;
    }

    public String getIndexingRequestedTopic() {
        return indexingRequestedTopic;
    }

    public void setIndexingRequestedTopic(String indexingRequestedTopic) {
        this.indexingRequestedTopic = indexingRequestedTopic;
    }

    public boolean isProcessingResultListenerEnabled() {
        return processingResultListenerEnabled;
    }

    public void setProcessingResultListenerEnabled(boolean processingResultListenerEnabled) {
        this.processingResultListenerEnabled = processingResultListenerEnabled;
    }

    public String getProcessingResultConsumerGroup() {
        return processingResultConsumerGroup;
    }

    public void setProcessingResultConsumerGroup(String processingResultConsumerGroup) {
        this.processingResultConsumerGroup = processingResultConsumerGroup;
    }

    public String getProcessingResultAutoOffsetReset() {
        return processingResultAutoOffsetReset;
    }

    public void setProcessingResultAutoOffsetReset(String processingResultAutoOffsetReset) {
        this.processingResultAutoOffsetReset = processingResultAutoOffsetReset;
    }

    public boolean isIndexingListenerEnabled() {
        return indexingListenerEnabled;
    }

    public void setIndexingListenerEnabled(boolean indexingListenerEnabled) {
        this.indexingListenerEnabled = indexingListenerEnabled;
    }

    public String getIndexingConsumerGroup() {
        return indexingConsumerGroup;
    }

    public void setIndexingConsumerGroup(String indexingConsumerGroup) {
        this.indexingConsumerGroup = indexingConsumerGroup;
    }

    public String getIndexingAutoOffsetReset() {
        return indexingAutoOffsetReset;
    }

    public void setIndexingAutoOffsetReset(String indexingAutoOffsetReset) {
        this.indexingAutoOffsetReset = indexingAutoOffsetReset;
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
