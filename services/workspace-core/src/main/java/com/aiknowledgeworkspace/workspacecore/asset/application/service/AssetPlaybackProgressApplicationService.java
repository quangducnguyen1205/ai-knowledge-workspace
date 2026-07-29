package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.SaveAssetPlaybackProgressCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidPlaybackProgressException;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetPlaybackProgressSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetPlaybackProgressUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetPlaybackProgressView;
import com.aiknowledgeworkspace.workspacecore.identity.api.CurrentUserContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orchestrates per-user playback progress.
 *
 * <p>Authorization always runs through the existing authorized Asset lookup before any progress
 * read or write, so a missing or foreign Asset stays indistinguishable from an absent one.
 * Playback progress never reads or mutates processing, transcript, indexing or media state.
 */
@Service
public class AssetPlaybackProgressApplicationService implements AssetPlaybackProgressUseCase {

    private final AssetQueryApplicationService assetQueries;
    private final AssetPlaybackProgressStore progressStore;
    private final AssetPlaybackProgressTransaction progressTransaction;
    private final CurrentUserContext currentUser;

    public AssetPlaybackProgressApplicationService(
            AssetQueryApplicationService assetQueries,
            AssetPlaybackProgressStore progressStore,
            AssetPlaybackProgressTransaction progressTransaction,
            CurrentUserContext currentUser
    ) {
        this.assetQueries = assetQueries;
        this.progressStore = progressStore;
        this.progressTransaction = progressTransaction;
        this.currentUser = currentUser;
    }

    @Override
    public AssetPlaybackProgressView getProgress(UUID assetId) {
        UUID authorizedAssetId = assetQueries.loadAuthorizedAsset(assetId).getId();
        return progressStore.find(authorizedAssetId, currentUser.getCurrentUserId())
                .map(snapshot -> toView(authorizedAssetId, snapshot))
                .orElseGet(() -> AssetPlaybackProgressView.unstarted(authorizedAssetId));
    }

    @Override
    public AssetPlaybackProgressView saveProgress(UUID assetId, SaveAssetPlaybackProgressCommand command) {
        long positionMs = validatePositionMs(command == null ? null : command.positionMs());
        boolean completed = command != null && Boolean.TRUE.equals(command.completed());
        UUID authorizedAssetId = assetQueries.loadAuthorizedAsset(assetId).getId();
        AssetPlaybackProgressSnapshot saved = progressTransaction.upsert(
                authorizedAssetId,
                currentUser.getCurrentUserId(),
                positionMs,
                completed,
                Instant.now()
        );
        return toView(authorizedAssetId, saved);
    }

    private long validatePositionMs(BigDecimal positionMs) {
        if (positionMs == null) {
            throw new InvalidPlaybackProgressException("positionMs is required");
        }
        if (positionMs.stripTrailingZeros().scale() > 0) {
            throw new InvalidPlaybackProgressException("positionMs must be a whole number of milliseconds");
        }
        if (positionMs.signum() < 0) {
            throw new InvalidPlaybackProgressException("positionMs must be greater than or equal to 0");
        }
        try {
            return positionMs.longValueExact();
        } catch (ArithmeticException exception) {
            throw new InvalidPlaybackProgressException("positionMs is outside the supported range");
        }
    }

    private AssetPlaybackProgressView toView(UUID assetId, AssetPlaybackProgressSnapshot snapshot) {
        return new AssetPlaybackProgressView(
                assetId,
                snapshot.positionMs(),
                snapshot.completed(),
                snapshot.updatedAt()
        );
    }
}
