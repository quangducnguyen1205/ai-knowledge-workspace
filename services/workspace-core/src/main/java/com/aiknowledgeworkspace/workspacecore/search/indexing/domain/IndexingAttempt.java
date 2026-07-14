package com.aiknowledgeworkspace.workspacecore.search.indexing.domain;

import com.aiknowledgeworkspace.workspacecore.search.indexing.application.AssetSearchIndexExecutionResult;

import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import java.util.UUID;

public record IndexingAttempt(
        UUID indexingJobId,
        IndexingAssetSource indexingSource,
        AssetSearchIndexExecutionResult result
) {
    public static IndexingAttempt completed(AssetSearchIndexExecutionResult result) {
        return new IndexingAttempt(result.indexingJobId(), null, result);
    }

    public static IndexingAttempt started(UUID indexingJobId, IndexingAssetSource indexingSource) {
        return new IndexingAttempt(indexingJobId, indexingSource, null);
    }
}
