package com.aiknowledgeworkspace.workspacecore.assistant.application.port;

import java.util.Optional;
import java.util.UUID;

public interface AssistantTranscriptContextPort {
    Optional<AssistantTranscriptContext> findSearchableTranscriptContext(
            UUID assetId, UUID workspaceId, String transcriptRowId, int window
    );
}
