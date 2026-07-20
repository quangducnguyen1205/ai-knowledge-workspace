package com.aiknowledgeworkspace.workspacecore.search.adapter.in.web;

import java.util.List;
import java.util.UUID;

public record SearchResponse(
        String query,
        UUID workspaceIdFilter,
        UUID assetIdFilter,
        int resultCount,
        List<SearchResultResponse> results
) {
    public SearchResponse {
        results = List.copyOf(results);
    }
}
