package com.aiknowledgeworkspace.workspacecore.search.api;

import com.aiknowledgeworkspace.workspacecore.search.application.model.TranscriptFingerprintRow;

public record IndexingRequestRow(
        String id,
        String videoId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text,
        String createdAt
) implements TranscriptFingerprintRow {
}
