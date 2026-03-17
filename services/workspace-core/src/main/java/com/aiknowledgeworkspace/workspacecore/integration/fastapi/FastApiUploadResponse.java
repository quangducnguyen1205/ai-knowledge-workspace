package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FastApiUploadResponse(
        @JsonProperty("task_id")
        String taskId,
        String status,
        @JsonProperty("video_id")
        String videoId
) {
}
