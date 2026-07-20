package com.aiknowledgeworkspace.workspacecore.search.api;

import com.aiknowledgeworkspace.workspacecore.search.application.model.TranscriptFingerprintRow;

public record IndexingRequestRow(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) implements TranscriptFingerprintRow {
}
