package com.aiknowledgeworkspace.workspacecore.asset.application.port.out;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;
import java.util.List;
import java.util.UUID;

public interface CanonicalTranscriptStore {

    List<AssetTranscriptRowView> load(UUID assetId);

    List<AssetTranscriptRowView> replace(UUID assetId, List<AssetTranscriptRowInput> rows);

    void delete(UUID assetId);
}
