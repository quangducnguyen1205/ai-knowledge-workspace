package com.aiknowledgeworkspace.workspacecore.search;

import java.util.UUID;

public record AssetIndexingHandleResult(
        UUID eventId,
        UUID indexingJobId,
        AssetSearchIndexJobStatus status,
        int indexedDocumentCount
) {
}
