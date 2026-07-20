package com.aiknowledgeworkspace.workspacecore.assistant.application.model;

import java.util.UUID;

public record AssistantCitation(UUID assetId, String transcriptRowId, Integer segmentIndex) {
}
