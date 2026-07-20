package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.configuration;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.fastapi")
public class FastApiProperties {

    private String baseUrl = "http://localhost:8000";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(30);
    private String assistantAnswerPath = "/internal/assistant/answer";
    private Duration assistantReadTimeout = Duration.ofSeconds(75);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        validateBaseUrl(baseUrl);
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

    public String getAssistantAnswerPath() {
        return assistantAnswerPath;
    }

    public void setAssistantAnswerPath(String assistantAnswerPath) {
        this.assistantAnswerPath = assistantAnswerPath;
    }

    public Duration getAssistantReadTimeout() {
        return assistantReadTimeout;
    }

    public void setAssistantReadTimeout(Duration assistantReadTimeout) {
        if (assistantReadTimeout == null || assistantReadTimeout.isZero() || assistantReadTimeout.isNegative()) {
            throw new IllegalArgumentException("integration.fastapi.assistant-read-timeout must be positive");
        }
        this.assistantReadTimeout = assistantReadTimeout;
    }

    private void validateBaseUrl(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("integration.fastapi.base-url must be a plain HTTP or HTTPS URI with a host");
        }
        if (value.indexOf('\\') >= 0 || value.indexOf('\'') >= 0 || value.indexOf('"') >= 0) {
            throw new IllegalArgumentException("integration.fastapi.base-url must not contain shell quoting or escaping");
        }

        try {
            URI uri = new URI(value);
            boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme());
            if (!supportedScheme || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException(
                        "integration.fastapi.base-url must be a plain HTTP or HTTPS URI with a host"
                );
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "integration.fastapi.base-url must be a plain HTTP or HTTPS URI with a host",
                    exception
            );
        }
    }
}
