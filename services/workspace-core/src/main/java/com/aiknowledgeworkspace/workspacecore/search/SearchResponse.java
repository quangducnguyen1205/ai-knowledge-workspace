package com.aiknowledgeworkspace.workspacecore.search;

import java.util.List;
import java.util.UUID;

public record SearchResponse(
        String query,
        UUID assetIdFilter,
        int resultCount,
        List<SearchResultResponse> results
) {
}
