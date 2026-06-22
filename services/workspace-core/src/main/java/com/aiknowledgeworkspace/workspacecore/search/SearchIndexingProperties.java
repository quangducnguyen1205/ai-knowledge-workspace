package com.aiknowledgeworkspace.workspacecore.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.search.indexing")
public class SearchIndexingProperties {

    private boolean autoRequestEnabled = false;

    public boolean isAutoRequestEnabled() {
        return autoRequestEnabled;
    }

    public void setAutoRequestEnabled(boolean autoRequestEnabled) {
        this.autoRequestEnabled = autoRequestEnabled;
    }
}
