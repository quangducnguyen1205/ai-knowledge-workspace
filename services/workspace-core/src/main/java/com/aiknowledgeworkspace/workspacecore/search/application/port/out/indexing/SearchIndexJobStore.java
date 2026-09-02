package com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing;

import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import java.time.Instant;
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

    /**
     * Counts indexing pressure by status in one pass. A job claimed for indexing at or before
     * {@code stuckBefore} is reported as stuck.
     */
    IndexingBacklogSnapshot loadBacklogSnapshot(Instant stuckBefore);

    AssetSearchIndexJob save(AssetSearchIndexJob job);

    void deleteByAssetId(UUID assetId);
}
