package com.aiknowledgeworkspace.workspacecore.assistant.application.port.out;

public record AssistantTranscriptSegment(
        String id,
        String videoId,
        Integer segmentIndex,
        Long startMs,
        Long endMs,
        String text,
        String createdAt
) {
}
