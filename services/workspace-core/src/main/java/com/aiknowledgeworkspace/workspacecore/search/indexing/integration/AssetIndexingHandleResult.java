package com.aiknowledgeworkspace.workspacecore.search.indexing.integration;

import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJobStatus;

import java.util.UUID;

public record AssetIndexingHandleResult(
        UUID eventId,
        UUID indexingJobId,
        AssetSearchIndexJobStatus status,
        int indexedDocumentCount
) {
}
