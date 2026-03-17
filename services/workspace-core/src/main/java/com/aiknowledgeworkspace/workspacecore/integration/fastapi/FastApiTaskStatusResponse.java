package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FastApiTaskStatusResponse(
        @JsonProperty("task_id")
        String taskId,
        String status,
        @JsonProperty("video_id")
        String videoId
) {
    // TODO: confirm the full upstream task status schema before turning this into a stable internal contract.
}
