package com.aiknowledgeworkspace.workspacecore.identity.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "current-user")
public class CurrentUserProperties {

    private String headerName = "X-Current-User-Id";
    private String sessionAttributeName = "CURRENT_USER_ID";
    private String defaultId = "local-dev-user";
    private boolean devFallbackEnabled = true;

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getSessionAttributeName() {
        return sessionAttributeName;
    }

    public void setSessionAttributeName(String sessionAttributeName) {
        this.sessionAttributeName = sessionAttributeName;
    }

    public String getDefaultId() {
        return defaultId;
    }

    public void setDefaultId(String defaultId) {
        this.defaultId = defaultId;
    }

    public boolean isDevFallbackEnabled() {
        return devFallbackEnabled;
    }

    public void setDevFallbackEnabled(boolean devFallbackEnabled) {
        this.devFallbackEnabled = devFallbackEnabled;
    }
}
