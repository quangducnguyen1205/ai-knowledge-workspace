package com.aiknowledgeworkspace.workspacecore.asset.application.port.in;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetPage;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetStatusView;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetView;
import java.util.List;
import java.util.UUID;

public interface AssetQueryUseCase {

    AssetView getAsset(UUID assetId);

    AssetPage listAssets(UUID workspaceId, Integer page, Integer size, AssetStatus status);

    AssetStatusView getAssetStatus(UUID assetId);

    List<AssetTranscriptRowView> getAssetTranscript(UUID assetId);

    AssetTranscriptContext getAssetTranscriptContext(UUID assetId, String transcriptRowId, Integer window);
}
