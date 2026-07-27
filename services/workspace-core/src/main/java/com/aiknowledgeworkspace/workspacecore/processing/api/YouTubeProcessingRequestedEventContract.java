package com.aiknowledgeworkspace.workspacecore.processing.api;

public final class YouTubeProcessingRequestedEventContract {

    public static final String EVENT_TYPE = "asset.processing.requested";
    public static final int EVENT_VERSION = 2;
    public static final String AGGREGATE_TYPE = "ASSET";
    public static final String SOURCE_TYPE = "YOUTUBE";

    private YouTubeProcessingRequestedEventContract() {
    }
}
