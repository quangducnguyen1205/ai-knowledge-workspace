package com.aiknowledgeworkspace.workspacecore.search.indexing.application.port.out;

import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SearchIndexJobStore {

    Optional<AssetSearchIndexJob> findById(UUID jobId);

    List<AssetSearchIndexJob> findByAssetAndStatuses(UUID assetId, Collection<AssetSearchIndexJobStatus> statuses);

    List<AssetSearchIndexJob> findByAssetFingerprintAndStatuses(
            UUID assetId,
            String fingerprint,
            Collection<AssetSearchIndexJobStatus> statuses
    );

    Optional<AssetSearchIndexJob> findLatestIndexed(UUID assetId, String fingerprint);

    Optional<AssetSearchIndexJob> findByRequestOutboxEventId(UUID eventId);

    AssetSearchIndexJob save(AssetSearchIndexJob job);

    void deleteByAssetId(UUID assetId);
}
