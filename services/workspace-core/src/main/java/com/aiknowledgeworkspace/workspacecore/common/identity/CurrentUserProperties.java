package com.aiknowledgeworkspace.workspacecore.common.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "current-user")
public class CurrentUserProperties {

    private String headerName = "X-Current-User-Id";
    private String defaultId = "local-dev-user";

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getDefaultId() {
        return defaultId;
    }

    public void setDefaultId(String defaultId) {
        this.defaultId = defaultId;
    }
}
