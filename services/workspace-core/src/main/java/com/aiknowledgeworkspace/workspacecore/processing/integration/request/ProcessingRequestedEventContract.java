package com.aiknowledgeworkspace.workspacecore.processing.integration.request;

public final class ProcessingRequestedEventContract {

    public static final String EVENT_TYPE = "asset.processing.requested";
    public static final int EVENT_VERSION = 1;
    public static final String AGGREGATE_TYPE = "Asset";

    private ProcessingRequestedEventContract() {
    }
}
