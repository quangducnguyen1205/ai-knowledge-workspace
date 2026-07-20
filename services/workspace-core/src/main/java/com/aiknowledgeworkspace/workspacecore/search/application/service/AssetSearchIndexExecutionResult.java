package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;

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
