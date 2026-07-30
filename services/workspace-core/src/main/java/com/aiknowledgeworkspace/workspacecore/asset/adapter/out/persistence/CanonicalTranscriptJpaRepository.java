package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CanonicalTranscriptJpaRepository extends JpaRepository<AssetTranscriptRowSnapshot, UUID> {

    List<AssetTranscriptRowSnapshot> findByAssetId(UUID assetId);

    List<AssetTranscriptRowSnapshot> findByAssetIdAndTranscriptRowIdIn(
            UUID assetId, Collection<String> transcriptRowIds
    );

    List<AssetTranscriptRowSnapshot> findByAssetIdAndSegmentIndexIn(
            UUID assetId, Collection<Integer> segmentIndexes
    );

    void deleteByAssetId(UUID assetId);
}
