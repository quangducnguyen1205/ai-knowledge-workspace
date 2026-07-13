package com.aiknowledgeworkspace.workspacecore.search.application;

import java.util.Optional;
import java.util.UUID;

public interface IndexingAssetPort {
    Optional<IndexingAssetSource> findCurrentIndexingSource(UUID assetId);

    IndexingAssetSource loadAuthorizedIndexingSourceForCompletedProcessing(UUID assetId, String videoId);

    void markTranscriptReady(UUID assetId);

    void markSearchable(UUID assetId);
}
