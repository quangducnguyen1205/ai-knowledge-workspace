package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetPlaybackProgressSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the read-then-write progress upsert inside one transaction. Authorization has already
 * happened by the time this boundary is entered.
 */
@Service
class AssetPlaybackProgressTransaction {

    private final AssetPlaybackProgressStore progressStore;

    AssetPlaybackProgressTransaction(AssetPlaybackProgressStore progressStore) {
        this.progressStore = progressStore;
    }

    @Transactional
    AssetPlaybackProgressSnapshot upsert(
            UUID assetId,
            String userId,
            long positionMs,
            boolean completed,
            Instant updatedAt
    ) {
        return progressStore.upsert(assetId, userId, positionMs, completed, updatedAt);
    }
}
