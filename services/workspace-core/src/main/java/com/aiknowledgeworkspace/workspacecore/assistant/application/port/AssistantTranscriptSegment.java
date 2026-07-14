package com.aiknowledgeworkspace.workspacecore.assistant.application.port;

public record AssistantTranscriptSegment(
        String id,
        String videoId,
        Integer segmentIndex,
        String text,
        String createdAt
) {
}
