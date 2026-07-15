package com.aiknowledgeworkspace.workspacecore.integration.fastapi.assistant.internal;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiConnectivityException;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiIntegrationException;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.InvalidFastApiResponseException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class FastApiAssistantClientImpl implements FastApiAssistantClient {

    private final RestClient fastApiAssistantRestClient;
    private final String assistantAnswerPath;

    FastApiAssistantClientImpl(
            @Qualifier("fastApiAssistantRestClient") RestClient fastApiAssistantRestClient,
            @Value("${integration.fastapi.assistant-answer-path:/internal/assistant/answer}") String assistantAnswerPath
    ) {
        this.fastApiAssistantRestClient = fastApiAssistantRestClient;
        this.assistantAnswerPath = assistantAnswerPath;
    }

    @Override
    public FastApiAssistantAnswerResponse answer(FastApiAssistantAnswerRequest request) {
        FastApiAssistantAnswerResponse response = execute(
                () -> fastApiAssistantRestClient.post()
                        .uri(assistantAnswerPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(FastApiAssistantAnswerResponse.class),
                "generate assistant answer"
        );
        validate(response);
        return response;
    }

    private void validate(FastApiAssistantAnswerResponse response) {
        if (response == null) {
            throw new InvalidFastApiResponseException("FastAPI assistant answer response body was empty");
        }
        if (response.answer() == null) {
            throw new InvalidFastApiResponseException("FastAPI assistant answer response did not include answer");
        }
        if (response.citedSourceIds() == null) {
            throw new InvalidFastApiResponseException(
                    "FastAPI assistant answer response did not include citedSourceIds"
            );
        }
        if (response.insufficientContext() == null) {
            throw new InvalidFastApiResponseException(
                    "FastAPI assistant answer response did not include insufficientContext"
            );
        }
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
