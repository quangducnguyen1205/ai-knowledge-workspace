package com.aiknowledgeworkspace.workspacecore.assistant.application.port.out;

import java.util.UUID;

public record AssistantProviderSource(
        String sourceId,
        UUID assetId,
        String assetTitle,
        String transcriptRowId,
        Integer segmentIndex,
        String createdAt,
        String text
) {
}
