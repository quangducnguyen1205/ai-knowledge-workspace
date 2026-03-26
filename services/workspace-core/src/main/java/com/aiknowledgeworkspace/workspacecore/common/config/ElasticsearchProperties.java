package com.aiknowledgeworkspace.workspacecore.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.elasticsearch")
public class ElasticsearchProperties {

    private String baseUrl = "http://localhost:9201";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(10);
    private String transcriptIndexName = "asset-transcript-rows";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getTranscriptIndexName() {
        return transcriptIndexName;
    }

    public void setTranscriptIndexName(String transcriptIndexName) {
        this.transcriptIndexName = transcriptIndexName;
    }
}
