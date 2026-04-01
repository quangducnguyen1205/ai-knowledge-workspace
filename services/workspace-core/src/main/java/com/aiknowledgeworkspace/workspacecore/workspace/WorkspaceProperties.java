package com.aiknowledgeworkspace.workspacecore.workspace;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace")
public class WorkspaceProperties {

    private UUID defaultId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private String defaultName = "Default Workspace";

    public UUID getDefaultId() {
        return defaultId;
    }

    public void setDefaultId(UUID defaultId) {
        this.defaultId = defaultId;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public void setDefaultName(String defaultName) {
        this.defaultName = defaultName;
    }
}
