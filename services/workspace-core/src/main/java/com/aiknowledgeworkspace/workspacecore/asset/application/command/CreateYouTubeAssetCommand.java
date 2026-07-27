package com.aiknowledgeworkspace.workspacecore.asset.application.command;

import java.util.UUID;

public record CreateYouTubeAssetCommand(
        UUID workspaceId,
        String url,
        String requestedTitle
) {
}
