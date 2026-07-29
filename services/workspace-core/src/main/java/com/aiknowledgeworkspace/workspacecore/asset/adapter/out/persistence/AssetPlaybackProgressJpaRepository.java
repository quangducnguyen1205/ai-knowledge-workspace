package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetPlaybackProgressJpaRepository
        extends JpaRepository<AssetPlaybackProgressEntry, AssetPlaybackProgressEntryId> {

    void deleteByAssetId(UUID assetId);
}
