package com.aiknowledgeworkspace.workspacecore.asset.application.model;

import java.util.List;

public record CanonicalTranscriptContextWindow(
        String requestedTranscriptRowId,
        Integer requestedSegmentIndex,
        CanonicalTranscriptContextRow matchedRow,
        List<CanonicalTranscriptContextRow> orderedRows
) {

    public CanonicalTranscriptContextWindow {
        orderedRows = orderedRows == null ? List.of() : List.copyOf(orderedRows);
    }
}
