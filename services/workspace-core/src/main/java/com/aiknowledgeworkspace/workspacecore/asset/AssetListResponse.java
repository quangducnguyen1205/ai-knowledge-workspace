package com.aiknowledgeworkspace.workspacecore.asset;

import java.util.List;

public record AssetListResponse(
        List<AssetSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
