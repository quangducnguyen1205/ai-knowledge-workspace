package com.aiknowledgeworkspace.workspacecore.search.indexing.application;

import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJobStatus;

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
