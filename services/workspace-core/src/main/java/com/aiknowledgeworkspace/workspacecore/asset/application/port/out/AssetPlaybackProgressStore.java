package com.aiknowledgeworkspace.workspacecore.asset.application.port.out;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetPlaybackProgressSnapshot;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AssetPlaybackProgressStore {

    Optional<AssetPlaybackProgressSnapshot> find(UUID assetId, String userId);

    /**
     * Deterministic last-write-wins upsert for one user and Asset. A later write replaces an
     * earlier value; there is no version field and no cross-device conflict resolution.
     */
    AssetPlaybackProgressSnapshot upsert(
            UUID assetId,
            String userId,
            long positionMs,
            boolean completed,
            Instant updatedAt
    );

    void deleteForAsset(UUID assetId);
}
