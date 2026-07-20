package com.aiknowledgeworkspace.workspacecore.search.application.query;

import java.util.List;
import java.util.UUID;

public record SearchResult(
        String query,
        UUID workspaceIdFilter,
        UUID assetIdFilter,
        List<SearchHit> hits
) {
    public SearchResult {
        hits = List.copyOf(hits);
    }
}
