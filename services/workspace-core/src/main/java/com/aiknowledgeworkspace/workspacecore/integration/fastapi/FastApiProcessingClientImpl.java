package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class FastApiProcessingClientImpl implements FastApiProcessingClient {

    private final RestClient fastApiRestClient;

    public FastApiProcessingClientImpl(RestClient fastApiRestClient) {
        this.fastApiRestClient = fastApiRestClient;
    }

    @Override
    public FastApiUploadResponse uploadVideo(Resource videoResource, String filename) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentDispositionFormData("file", filename);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(videoResource, fileHeaders));

        return fastApiRestClient.post()
                .uri("/videos/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(FastApiUploadResponse.class);
    }

    @Override
    public FastApiTaskStatusResponse getTaskStatus(String taskId) {
        return fastApiRestClient.get()
                .uri("/videos/tasks/{taskId}", taskId)
                .retrieve()
                .body(FastApiTaskStatusResponse.class);
    }

    @Override
    public FastApiVideoReadResponse getVideo(String videoId) {
        return fastApiRestClient.get()
                .uri("/videos/{videoId}", videoId)
                .retrieve()
                .body(FastApiVideoReadResponse.class);
    }

    @Override
    public List<FastApiTranscriptRowResponse> getTranscript(String videoId) {
        FastApiTranscriptRowResponse[] rows = fastApiRestClient.get()
                .uri("/videos/{videoId}/transcript", videoId)
                .retrieve()
                .body(FastApiTranscriptRowResponse[].class);

        if (rows == null) {
            return List.of();
        }

        return List.of(rows);
    }
}
