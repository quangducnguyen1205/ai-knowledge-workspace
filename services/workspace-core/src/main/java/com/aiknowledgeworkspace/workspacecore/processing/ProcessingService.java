package com.aiknowledgeworkspace.workspacecore.processing;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiProcessingClient;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTaskStatusResponse;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProcessingService {

    private final FastApiProcessingClient fastApiProcessingClient;

    public ProcessingService(FastApiProcessingClient fastApiProcessingClient) {
        this.fastApiProcessingClient = fastApiProcessingClient;
    }

    public FastApiTaskStatusResponse getUpstreamTaskStatus(String taskId) {
        return fastApiProcessingClient.getTaskStatus(taskId);
    }

    public List<FastApiTranscriptRowResponse> getUpstreamTranscript(String videoId) {
        return fastApiProcessingClient.getTranscript(videoId);
    }

    // TODO: decide how Spring should classify an asset when FastAPI reports success or ready
    // TODO: but the transcript endpoint still returns no rows.
}
