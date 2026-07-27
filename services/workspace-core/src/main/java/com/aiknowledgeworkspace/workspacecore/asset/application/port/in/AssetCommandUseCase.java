package com.aiknowledgeworkspace.workspacecore.asset.application.port.in;

import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetView;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetProcessingResult;
import java.util.UUID;

public interface AssetCommandUseCase {

    AssetView updateTitle(UUID assetId, String title);

    AssetProcessingResult retryProcessing(UUID assetId);

    void delete(UUID assetId);
}
