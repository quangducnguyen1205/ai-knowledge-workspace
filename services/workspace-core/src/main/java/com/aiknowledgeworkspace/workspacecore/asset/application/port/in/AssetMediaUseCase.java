package com.aiknowledgeworkspace.workspacecore.asset.application.port.in;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetMediaDescriptor;
import java.io.InputStream;
import java.util.UUID;

public interface AssetMediaUseCase {

    AssetMediaDescriptor resolve(UUID assetId);

    InputStream openStream(AssetMediaDescriptor descriptor, long offset, long length);
}
