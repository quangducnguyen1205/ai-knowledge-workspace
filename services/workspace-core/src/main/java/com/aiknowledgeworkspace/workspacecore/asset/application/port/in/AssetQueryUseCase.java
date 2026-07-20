package com.aiknowledgeworkspace.workspacecore.asset.application.port.in;

import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptContext;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetPage;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetStatusView;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetView;
import java.util.List;
import java.util.UUID;

public interface AssetQueryUseCase {

    AssetView getAsset(UUID assetId);

    AssetPage listAssets(UUID workspaceId, Integer page, Integer size, AssetStatus status);

    AssetStatusView getAssetStatus(UUID assetId);

    List<AssetTranscriptRowView> getAssetTranscript(UUID assetId);

    AssetTranscriptContext getAssetTranscriptContext(UUID assetId, String transcriptRowId, Integer window);
}
