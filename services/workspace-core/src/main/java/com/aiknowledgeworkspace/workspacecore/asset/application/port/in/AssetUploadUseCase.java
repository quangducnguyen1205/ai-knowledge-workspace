package com.aiknowledgeworkspace.workspacecore.asset.application.port.in;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.AssetUploadCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetUploadResult;

public interface AssetUploadUseCase {

    AssetUploadResult upload(AssetUploadCommand command);
}
