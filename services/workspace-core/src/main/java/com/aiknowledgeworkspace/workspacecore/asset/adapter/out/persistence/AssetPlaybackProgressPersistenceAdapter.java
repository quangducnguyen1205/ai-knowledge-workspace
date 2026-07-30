package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetPlaybackProgressSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.ResumableAssetPlayback;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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
    public List<ResumableAssetPlayback> findResumable(String userId, UUID workspaceId, int limit) {
        if (userId == null || workspaceId == null || limit <= 0) {
            return List.of();
        }
        return progressRepository.findResumable(userId, workspaceId, PageRequest.of(0, limit)).stream()
                .map(row -> new ResumableAssetPlayback(
                        row.assetId(),
                        row.workspaceId(),
                        row.assetTitle(),
                        row.sourceType(),
                        row.positionMs(),
                        row.completed(),
                        row.updatedAt()
                ))
                .toList();
    }

    @Override
    public void deleteForAsset(UUID assetId) {
        progressRepository.deleteByAssetId(assetId);
    }
}
