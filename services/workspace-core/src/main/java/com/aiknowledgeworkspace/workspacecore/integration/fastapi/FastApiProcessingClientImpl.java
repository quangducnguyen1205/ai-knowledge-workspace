package com.aiknowledgeworkspace.workspacecore.integration.fastapi;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FastApiProcessingClientImpl implements FastApiProcessingClient {

    private final RestClient fastApiRestClient;

    public FastApiProcessingClientImpl(RestClient fastApiRestClient) {
        this.fastApiRestClient = fastApiRestClient;
    }

    @Override
    public FastApiUploadResponse uploadVideo(Resource videoResource, String filename, String title) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentDispositionFormData("file", filename);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(videoResource, fileHeaders));
        body.add("title", title);

        return execute(
                () -> fastApiRestClient.post()
                        .uri("/videos/upload")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body)
                        .retrieve()
                        .body(FastApiUploadResponse.class),
                "upload video"
        );
    }

    @Override
    public FastApiTaskStatusResponse getTaskStatus(String taskId) {
        return execute(
                () -> fastApiRestClient.get()
                        .uri("/videos/tasks/{taskId}", taskId)
                        .retrieve()
                        .body(FastApiTaskStatusResponse.class),
                "read task status"
        );
    }

    @Override
    public FastApiVideoReadResponse getVideo(String videoId) {
        return execute(
                () -> fastApiRestClient.get()
                        .uri("/videos/{videoId}", videoId)
                        .retrieve()
                        .body(FastApiVideoReadResponse.class),
                "read video"
        );
    }

    @Override
    public List<FastApiTranscriptRowResponse> getTranscript(String videoId) {
        FastApiTranscriptRowResponse[] rows = execute(
                () -> fastApiRestClient.get()
                        .uri("/videos/{videoId}/transcript", videoId)
                        .retrieve()
                        .body(FastApiTranscriptRowResponse[].class),
                "read transcript"
        );

        if (rows == null) {
            return List.of();
        }

        return List.of(rows);
    }

    private <T> T execute(Supplier<T> operation, String description) {
        try {
            return operation.get();
        } catch (ResourceAccessException exception) {
            throw new FastApiTimeoutException("FastAPI timeout while trying to " + description, exception);
        } catch (RestClientResponseException exception) {
            throw new FastApiIntegrationException(
                    "FastAPI returned HTTP " + exception.getStatusCode().value() + " while trying to " + description,
                    exception
            );
        } catch (RestClientException exception) {
            throw new FastApiIntegrationException("FastAPI request failed while trying to " + description, exception);
        }
    }
}
