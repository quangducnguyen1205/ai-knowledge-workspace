package com.aiknowledgeworkspace.workspacecore.search.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator control for reconstructing the search projection. The command defaults to
 * {@link SearchRebuildCommand#NONE} so an ordinary start never rebuilds anything: this runs only
 * when a person asks for it.
 */
@ConfigurationProperties(prefix = "workspace.search.rebuild")
public class SearchRebuildProperties {

    private SearchRebuildCommand command = SearchRebuildCommand.NONE;
    private int batchSize = 50;

    public SearchRebuildCommand getCommand() {
        return command;
    }

    public void setCommand(SearchRebuildCommand command) {
        this.command = command;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("workspace.search.rebuild.batch-size must be between 1 and 1000");
        }
        this.batchSize = batchSize;
    }
}
