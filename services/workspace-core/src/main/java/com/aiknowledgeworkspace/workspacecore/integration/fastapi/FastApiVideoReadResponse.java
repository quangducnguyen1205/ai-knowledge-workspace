package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FastApiVideoReadResponse(
        @JsonAlias({"id", "video_id"})
        String videoId,
        String status
) {
    // TODO: confirm the exact upstream video read fields before expanding this DTO.
}
