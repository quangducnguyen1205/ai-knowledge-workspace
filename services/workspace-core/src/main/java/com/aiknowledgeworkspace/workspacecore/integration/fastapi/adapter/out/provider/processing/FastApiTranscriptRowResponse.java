package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.processing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FastApiTranscriptRowResponse(
        String id,
        @JsonProperty("video_id")
        String videoId,
        @JsonProperty("segment_index")
        Integer segmentIndex,
        @JsonProperty("start_ms")
        Long startMs,
        @JsonProperty("end_ms")
        Long endMs,
        String text,
        @JsonProperty("created_at")
        String createdAt
) {
}
