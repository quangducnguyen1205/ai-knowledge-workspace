package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CanonicalTranscriptJpaRepository extends JpaRepository<AssetTranscriptRowSnapshot, UUID> {

    List<AssetTranscriptRowSnapshot> findByAssetId(UUID assetId);

    /**
     * First page of assets that hold canonical transcript rows. Ordered by asset so the caller can
     * keyset-page through them without holding the whole table, and without a row ever being
     * visited twice or skipped.
     */
    @Query("""
            select distinct row.assetId from AssetTranscriptRowSnapshot row
            order by row.assetId asc
            """)
    List<UUID> findDistinctAssetIds(Pageable pageable);

    @Query("""
            select distinct row.assetId from AssetTranscriptRowSnapshot row
            where row.assetId > :afterAssetId
            order by row.assetId asc
            """)
    List<UUID> findDistinctAssetIdsAfter(@Param("afterAssetId") UUID afterAssetId, Pageable pageable);

    List<AssetTranscriptRowSnapshot> findByAssetIdAndTranscriptRowIdIn(
            UUID assetId, Collection<String> transcriptRowIds
    );

    List<AssetTranscriptRowSnapshot> findByAssetIdAndSegmentIndexIn(
            UUID assetId, Collection<Integer> segmentIndexes
    );

    void deleteByAssetId(UUID assetId);
}
