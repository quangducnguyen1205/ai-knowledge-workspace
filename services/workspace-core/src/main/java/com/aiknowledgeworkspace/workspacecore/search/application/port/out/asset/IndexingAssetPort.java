package com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndexingAssetPort {
    Optional<IndexingAssetSource> findCurrentIndexingSource(UUID assetId);

    /**
     * Ids of assets whose canonical state should appear in the search projection, ordered by asset
     * and starting after {@code afterAssetId} ({@code null} starts from the beginning), bounded by
     * {@code limit}. Used to page through canonical truth when rebuilding the projection.
     */
    List<UUID> findProjectionSourceAssetIds(UUID afterAssetId, int limit);

    IndexingAssetSource loadAuthorizedIndexingSource(UUID assetId);

    void markTranscriptReady(UUID assetId);

    void markSearchable(UUID assetId);
}
