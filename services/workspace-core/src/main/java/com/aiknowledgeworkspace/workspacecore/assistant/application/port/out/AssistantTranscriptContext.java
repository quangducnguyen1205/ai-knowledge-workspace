package com.aiknowledgeworkspace.workspacecore.assistant.application.port.out;

import java.util.List;
import java.util.UUID;

public record AssistantTranscriptContext(
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer hitSegmentIndex,
        int window,
        List<AssistantTranscriptSegment> rows
) {
    public AssistantTranscriptContext {
        rows = List.copyOf(rows);
    }
}
