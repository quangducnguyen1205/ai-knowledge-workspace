package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetPlaybackProgressSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AssetPlaybackProgressPersistenceAdapter implements AssetPlaybackProgressStore {

    private final AssetPlaybackProgressJpaRepository progressRepository;

    AssetPlaybackProgressPersistenceAdapter(AssetPlaybackProgressJpaRepository progressRepository) {
        this.progressRepository = progressRepository;
    }

    @Override
    public Optional<AssetPlaybackProgressSnapshot> find(UUID assetId, String userId) {
        return progressRepository.findById(new AssetPlaybackProgressEntryId(assetId, userId))
                .map(entry -> new AssetPlaybackProgressSnapshot(
                        entry.getPositionMs(),
                        entry.isCompleted(),
                        entry.getUpdatedAt()
                ));
    }

    @Override
    public AssetPlaybackProgressSnapshot upsert(
            UUID assetId,
            String userId,
            long positionMs,
            boolean completed,
            Instant updatedAt
    ) {
        progressRepository.upsert(assetId, userId, positionMs, completed, updatedAt);
        return new AssetPlaybackProgressSnapshot(positionMs, completed, updatedAt);
    }

    @Override
    public void deleteForAsset(UUID assetId) {
        progressRepository.deleteByAssetId(assetId);
    }
}
