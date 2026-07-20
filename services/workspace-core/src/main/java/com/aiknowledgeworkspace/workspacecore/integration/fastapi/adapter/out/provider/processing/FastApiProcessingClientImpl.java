package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.processing;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.common.FastApiConnectivityException;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.common.FastApiIntegrationException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class FastApiProcessingClientImpl implements FastApiProcessingClient {

    private final RestClient fastApiRestClient;

    FastApiProcessingClientImpl(@Qualifier("fastApiRestClient") RestClient fastApiRestClient) {
        this.fastApiRestClient = fastApiRestClient;
    }

    @Override
    public List<FastApiTranscriptRowResponse> getTranscriptArtifactRows(String processingRequestId) {
        FastApiTranscriptRowResponse[] rows = execute(
                () -> fastApiRestClient.get()
                        .uri("/internal/processing-requests/{processingRequestId}/transcript-rows", processingRequestId)
                        .retrieve().body(FastApiTranscriptRowResponse[].class),
                "read transcript artifact rows"
        );
        return rows == null ? List.of() : List.of(rows);
    }

    private <T> T execute(Supplier<T> operation, String description) {
        try {
            return operation.get();
        } catch (ResourceAccessException exception) {
            throw new FastApiConnectivityException("FastAPI timeout while trying to " + description, exception);
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
