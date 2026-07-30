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
