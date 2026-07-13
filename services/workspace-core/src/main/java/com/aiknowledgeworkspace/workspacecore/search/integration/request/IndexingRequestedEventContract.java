package com.aiknowledgeworkspace.workspacecore.search.integration.request;

public final class IndexingRequestedEventContract {

    public static final String EVENT_TYPE = "asset.indexing.requested";
    public static final int EVENT_VERSION = 1;
    public static final String AGGREGATE_TYPE = "ASSET";

    private IndexingRequestedEventContract() {
    }
}
