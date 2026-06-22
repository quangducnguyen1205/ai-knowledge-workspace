package com.aiknowledgeworkspace.workspacecore.search;

import java.util.UUID;

public record AssetSearchIndexExecutionResult(
        UUID indexingJobId,
        AssetSearchIndexJobStatus status,
        int indexedDocumentCount
) {
    public boolean indexed() {
        return status == AssetSearchIndexJobStatus.INDEXED;
    }
}
