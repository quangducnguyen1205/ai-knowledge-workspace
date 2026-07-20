package com.aiknowledgeworkspace.workspacecore.search.application.result;

import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;

import java.util.UUID;

public record AssetIndexingHandleResult(
        UUID eventId,
        UUID indexingJobId,
        AssetSearchIndexJobStatus status,
        int indexedDocumentCount
) {
}
