package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CanonicalTranscriptJpaRepository extends JpaRepository<AssetTranscriptRowSnapshot, UUID> {

    List<AssetTranscriptRowSnapshot> findByAssetId(UUID assetId);

    void deleteByAssetId(UUID assetId);
}
