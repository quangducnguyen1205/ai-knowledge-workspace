package com.aiknowledgeworkspace.workspacecore.asset.application.port.in;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.CreateYouTubeAssetCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetProcessingResult;

public interface YouTubeAssetCreationUseCase {

    AssetProcessingResult create(CreateYouTubeAssetCommand command);
}
