package com.aiknowledgeworkspace.workspacecore.search.smoke;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.search.smoke")
public class SearchSmokeProperties {

    private SearchSmokeCommand command = SearchSmokeCommand.NONE;
    private UUID indexingOutboxEventId;

    public SearchSmokeCommand getCommand() {
        return command;
    }

    public void setCommand(SearchSmokeCommand command) {
        this.command = command;
    }

    public UUID getIndexingOutboxEventId() {
        return indexingOutboxEventId;
    }

    public void setIndexingOutboxEventId(UUID indexingOutboxEventId) {
        this.indexingOutboxEventId = indexingOutboxEventId;
    }
}
