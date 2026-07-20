package com.aiknowledgeworkspace.workspacecore.asset.application.port.in;

import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetView;
import java.util.UUID;

public interface AssetCommandUseCase {

    AssetView updateTitle(UUID assetId, String title);

    void delete(UUID assetId);
}
