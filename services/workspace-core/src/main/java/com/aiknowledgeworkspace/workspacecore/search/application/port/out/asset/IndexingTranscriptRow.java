package com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset;

import com.aiknowledgeworkspace.workspacecore.search.application.model.TranscriptFingerprintRow;

public record IndexingTranscriptRow(
        String id,
        String videoId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text,
        String createdAt
) implements TranscriptFingerprintRow {
}
