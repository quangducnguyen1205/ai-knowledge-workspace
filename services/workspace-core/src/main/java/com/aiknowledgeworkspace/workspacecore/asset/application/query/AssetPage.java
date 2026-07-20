package com.aiknowledgeworkspace.workspacecore.asset.application.query;

import java.util.List;

public record AssetPage(
        List<AssetSummary> items,
        int page,
        int size,
        int totalElements,
        int totalPages,
        boolean hasNext
) {
    public AssetPage {
        items = List.copyOf(items);
    }
}
