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
                .map(this::toSnapshot);
    }

    @Override
    public AssetPlaybackProgressSnapshot upsert(
            UUID assetId,
            String userId,
            long positionMs,
            boolean completed,
            Instant updatedAt
    ) {
        AssetPlaybackProgressEntry entry = progressRepository
                .findById(new AssetPlaybackProgressEntryId(assetId, userId))
                .orElseGet(() -> new AssetPlaybackProgressEntry(
                        assetId, userId, positionMs, completed, updatedAt
                ));
        entry.apply(positionMs, completed, updatedAt);
        return toSnapshot(progressRepository.save(entry));
    }

    @Override
    public void deleteForAsset(UUID assetId) {
        progressRepository.deleteByAssetId(assetId);
    }

    private AssetPlaybackProgressSnapshot toSnapshot(AssetPlaybackProgressEntry entry) {
        return new AssetPlaybackProgressSnapshot(
                entry.getPositionMs(),
                entry.isCompleted(),
                entry.getUpdatedAt()
        );
    }
}
