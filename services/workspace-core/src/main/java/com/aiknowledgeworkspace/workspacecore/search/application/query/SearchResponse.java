package com.aiknowledgeworkspace.workspacecore.search.application.query;

import java.util.List;
import java.util.UUID;

public record SearchResponse(
        String query,
        UUID workspaceIdFilter,
        UUID assetIdFilter,
        int resultCount,
        List<SearchResultResponse> results
) {
}
