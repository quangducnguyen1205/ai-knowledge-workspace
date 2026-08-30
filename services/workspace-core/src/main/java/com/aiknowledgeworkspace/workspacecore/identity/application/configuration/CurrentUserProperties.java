package com.aiknowledgeworkspace.workspacecore.identity.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "current-user")
public class CurrentUserProperties {

    private String sessionAttributeName = "CURRENT_USER_ID";

    public String getSessionAttributeName() {
        return sessionAttributeName;
    }

    public void setSessionAttributeName(String sessionAttributeName) {
        this.sessionAttributeName = sessionAttributeName;
    }
}
