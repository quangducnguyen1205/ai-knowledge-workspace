package com.aiknowledgeworkspace.workspacecore.search.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetSearchIndexJobJpaRepository extends JpaRepository<AssetSearchIndexJob, UUID> {

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
