package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.relay.IndexingRequestRelayProperties;
import org.springframework.stereotype.Component;

@Component
public class SearchAsyncConfiguration {

    private final SearchIndexingProperties indexingProperties;
    private final IndexingRequestRelayProperties indexingRelayProperties;

    public SearchAsyncConfiguration(
            SearchIndexingProperties indexingProperties,
            IndexingRequestRelayProperties indexingRelayProperties
    ) {
        this.indexingProperties = indexingProperties;
        this.indexingRelayProperties = indexingRelayProperties;
    }

    public boolean isAutoRequestEnabled() {
        return indexingProperties.isAutoRequestEnabled();
    }

    public boolean isIndexingRelayEnabled() {
        return indexingRelayProperties.isEnabled();
    }
}
