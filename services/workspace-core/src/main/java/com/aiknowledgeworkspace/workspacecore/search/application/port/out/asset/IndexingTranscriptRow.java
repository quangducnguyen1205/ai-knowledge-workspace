package com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset;

import com.aiknowledgeworkspace.workspacecore.search.application.model.TranscriptFingerprintRow;

public record IndexingTranscriptRow(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) implements TranscriptFingerprintRow {
}
