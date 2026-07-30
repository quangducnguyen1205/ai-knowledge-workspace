package com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset;

import java.util.List;
import java.util.UUID;

public record SearchCanonicalContext(
        UUID assetId,
        String requestedTranscriptRowId,
        Integer requestedSegmentIndex,
        SearchCanonicalContextRow matchedRow,
        List<SearchCanonicalContextRow> orderedRows
) {

    public SearchCanonicalContext {
        orderedRows = orderedRows == null ? List.of() : List.copyOf(orderedRows);
    }
}
