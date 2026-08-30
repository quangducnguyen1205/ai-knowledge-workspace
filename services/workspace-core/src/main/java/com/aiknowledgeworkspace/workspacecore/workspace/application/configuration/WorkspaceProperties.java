package com.aiknowledgeworkspace.workspacecore.workspace.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace")
public class WorkspaceProperties {

    private String defaultName = "Default Workspace";

    public String getDefaultName() {
        return defaultName;
    }

    public void setDefaultName(String defaultName) {
        this.defaultName = defaultName;
    }
}
