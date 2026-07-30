package com.aiknowledgeworkspace.workspacecore.asset.application.port.out;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetPlaybackProgressSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.ResumableAssetPlayback;
import java.time.Instant;
import java.util.List;
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

    /**
     * Resumable progress for one user in one Workspace, newest first with the Asset ID as a
     * deterministic tie break. Only started, incomplete progress of an Asset that still exists in
     * that Workspace is returned. This is a bounded read; it never writes.
     */
    List<ResumableAssetPlayback> findResumable(String userId, UUID workspaceId, int limit);

    void deleteForAsset(UUID assetId);
}
