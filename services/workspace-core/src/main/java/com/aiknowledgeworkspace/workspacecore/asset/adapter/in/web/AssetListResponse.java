package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web;

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
