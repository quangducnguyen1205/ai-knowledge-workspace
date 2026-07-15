package com.aiknowledgeworkspace.workspacecore.search.application.port.out;

import java.util.List;
import java.util.UUID;

public record TranscriptSearchQuery(
        String query,
        UUID workspaceId,
        UUID assetId,
        List<UUID> eligibleAssetIds
) {

    public TranscriptSearchQuery {
        eligibleAssetIds = List.copyOf(eligibleAssetIds);
    }
}
