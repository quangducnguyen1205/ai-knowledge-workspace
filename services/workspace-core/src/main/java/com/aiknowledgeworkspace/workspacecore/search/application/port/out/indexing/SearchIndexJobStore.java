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

    /**
     * Ids of jobs whose indexing claim was taken at or before {@code cutoff}, oldest first. The
     * claim instant is the job's {@code updatedAt}, stamped when the attempt began.
     */
    List<UUID> findStaleIndexingIds(AssetSearchIndexJobStatus indexing, Instant cutoff, int limit);

    /**
     * Takes over a stale indexing claim by refreshing it, conditional on the job still being
     * claimed and still being stale. Returns the number of rows changed, so a worker that loses the
     * race — to another instance, or to the attempt finishing — sees {@code 0} and stands down.
     * The status is deliberately unchanged: the job stays claimed, now by this worker.
     */
    int claimStaleIndexingJob(UUID jobId, AssetSearchIndexJobStatus indexing, Instant cutoff, Instant now);

    AssetSearchIndexJob save(AssetSearchIndexJob job);

    void deleteByAssetId(UUID assetId);
}
