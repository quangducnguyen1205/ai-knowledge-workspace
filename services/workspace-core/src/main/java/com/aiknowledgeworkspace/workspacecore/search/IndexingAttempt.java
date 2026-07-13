package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import java.util.UUID;

record IndexingAttempt(
        UUID indexingJobId,
        IndexingAssetSource indexingSource,
        AssetSearchIndexExecutionResult result
) {
    static IndexingAttempt completed(AssetSearchIndexExecutionResult result) {
        return new IndexingAttempt(result.indexingJobId(), null, result);
    }

    static IndexingAttempt started(UUID indexingJobId, IndexingAssetSource indexingSource) {
        return new IndexingAttempt(indexingJobId, indexingSource, null);
    }
}
