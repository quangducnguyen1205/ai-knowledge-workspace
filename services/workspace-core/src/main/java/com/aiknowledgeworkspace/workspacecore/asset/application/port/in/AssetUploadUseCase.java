package com.aiknowledgeworkspace.workspacecore.asset.application.port.in;

import com.aiknowledgeworkspace.workspacecore.asset.application.upload.AssetUploadCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.upload.AssetUploadResult;

public interface AssetUploadUseCase {

    AssetUploadResult upload(AssetUploadCommand command);
}
