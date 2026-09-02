package com.aiknowledgeworkspace.workspacecore.search.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.IndexingBacklogSnapshot;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssetSearchIndexJobJpaRepository extends JpaRepository<AssetSearchIndexJob, UUID> {

    @Query("""
            select new com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.IndexingBacklogSnapshot(
                count(case when job.status = :pendingStatus then 1 end),
                count(case when job.status = :indexingStatus then 1 end),
                count(case when job.status = :failedStatus then 1 end),
                count(case when job.status = :indexingStatus and job.updatedAt <= :stuckBefore then 1 end),
                min(case when job.status = :indexingStatus and job.updatedAt <= :stuckBefore
                    then job.updatedAt end)
            )
            from AssetSearchIndexJob job
            where job.status in (:pendingStatus, :indexingStatus, :failedStatus)
            """)
    IndexingBacklogSnapshot loadBacklogSnapshot(
            @Param("pendingStatus") AssetSearchIndexJobStatus pendingStatus,
            @Param("indexingStatus") AssetSearchIndexJobStatus indexingStatus,
            @Param("failedStatus") AssetSearchIndexJobStatus failedStatus,
            @Param("stuckBefore") Instant stuckBefore
    );

    List<AssetSearchIndexJob> findByAssetIdAndStatusIn(UUID assetId, Collection<AssetSearchIndexJobStatus> statuses);

    List<AssetSearchIndexJob> findByAssetIdAndSnapshotFingerprintAndStatusIn(
            UUID assetId,
            String snapshotFingerprint,
            Collection<AssetSearchIndexJobStatus> statuses
    );

    Optional<AssetSearchIndexJob> findFirstByAssetIdAndSnapshotFingerprintAndStatusOrderByIndexedAtDesc(
            UUID assetId,
            String snapshotFingerprint,
            AssetSearchIndexJobStatus status
    );

    Optional<AssetSearchIndexJob> findByRequestOutboxEventId(UUID requestOutboxEventId);

    void deleteByAssetId(UUID assetId);
}
