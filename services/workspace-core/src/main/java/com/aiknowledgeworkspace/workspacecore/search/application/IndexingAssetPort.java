package com.aiknowledgeworkspace.workspacecore.search.application;

import java.util.Optional;
import java.util.UUID;

public interface IndexingAssetPort {
    Optional<IndexingAssetSource> findCurrentIndexingSource(UUID assetId);

    IndexingAssetSource loadAuthorizedIndexingSource(UUID assetId);

    void markTranscriptReady(UUID assetId);

    void markSearchable(UUID assetId);
}
