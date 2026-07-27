package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

import java.util.UUID;

public record CreateYouTubeAssetRequest(
        UUID workspaceId,
        String url,
        String title
) {
}
