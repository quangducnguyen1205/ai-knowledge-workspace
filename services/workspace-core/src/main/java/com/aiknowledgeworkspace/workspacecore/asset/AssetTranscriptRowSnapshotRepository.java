package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetTranscriptRowSnapshotRepository extends JpaRepository<AssetTranscriptRowSnapshot, UUID> {

    List<AssetTranscriptRowSnapshot> findByAssetId(UUID assetId);

    void deleteByAssetId(UUID assetId);
}
