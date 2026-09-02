package com.aiknowledgeworkspace.workspacecore.asset.application.port.out;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextTarget;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextWindow;
import java.util.List;
import java.util.UUID;

public interface CanonicalTranscriptStore {

    List<AssetTranscriptRowView> load(UUID assetId);

    /**
     * Ids of assets that hold canonical transcript rows, ordered by asset and starting after
     * {@code afterAssetId} (or from the beginning when it is {@code null}). Bounded by
     * {@code limit} so a caller can page through the whole set without loading it.
     */
    List<UUID> findAssetIdsWithCanonicalRows(UUID afterAssetId, int limit);

    /**
     * Loads only the requested canonical rows of one Asset. Identity is the stored
     * {@code transcriptRowId}; rows that never received one keep the {@code segment-<index>}
     * convention. A supplied identifier never falls back to a different row.
     */
    List<AssetTranscriptRowView> loadCanonicalRows(UUID assetId, List<String> transcriptRowIds);

    List<CanonicalTranscriptContextWindow> loadContextWindows(
            UUID assetId,
            List<CanonicalTranscriptContextTarget> targets
    );

    List<AssetTranscriptRowView> replace(UUID assetId, List<AssetTranscriptRowInput> rows);

    void delete(UUID assetId);
}
