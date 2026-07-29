package com.aiknowledgeworkspace.workspacecore.asset.application.port.in;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.SaveAssetPlaybackProgressCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetPlaybackProgressView;
import java.util.UUID;

/**
 * Per-user playback position for one Asset. This is user interaction state; it is independent of
 * source type, processing status, transcript availability and derived search state.
 */
public interface AssetPlaybackProgressUseCase {

    AssetPlaybackProgressView getProgress(UUID assetId);

    AssetPlaybackProgressView saveProgress(UUID assetId, SaveAssetPlaybackProgressCommand command);
}
