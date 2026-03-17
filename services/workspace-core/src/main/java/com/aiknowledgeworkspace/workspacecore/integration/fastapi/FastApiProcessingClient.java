package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

import java.util.List;
import org.springframework.core.io.Resource;

public interface FastApiProcessingClient {

    FastApiUploadResponse uploadVideo(Resource videoResource, String filename);

    FastApiTaskStatusResponse getTaskStatus(String taskId);

    FastApiVideoReadResponse getVideo(String videoId);

    List<FastApiTranscriptRowResponse> getTranscript(String videoId);
}
