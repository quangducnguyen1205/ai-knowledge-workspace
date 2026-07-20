package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.common.FastApiIntegrationException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FastApiProcessingClientImplTest {

    private MockRestServiceServer mockServer;
    private FastApiProcessingClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new FastApiProcessingClientImpl(builder.build());
    }

    @Test
    void deserializesFastApiSnakeCaseTranscriptArtifactRowsAndPreservesTheirOrder() {
        UUID processingRequestId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        mockServer.expect(once(), requestTo(
                        "http://localhost:8000/internal/processing-requests/" + processingRequestId
                                + "/transcript-rows"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "id": "row-a",
                            "video_id": "video-a",
                            "segment_index": 0,
                            "text": "sanitized transcript text",
                            "created_at": "2026-01-01T00:00:00Z"
                          },
                          {
                            "id": "row-b",
                            "video_id": "video-a",
                            "segment_index": 1,
                            "text": "next sanitized transcript text",
                            "created_at": "2026-01-01T00:00:01Z"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var rows = client.getTranscriptArtifactRows(processingRequestId.toString());

        assertThat(rows).extracting(
                FastApiTranscriptRowResponse::id,
                FastApiTranscriptRowResponse::videoId,
                FastApiTranscriptRowResponse::segmentIndex,
                FastApiTranscriptRowResponse::text,
                FastApiTranscriptRowResponse::createdAt
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple(
                        "row-a", "video-a", 0, "sanitized transcript text", "2026-01-01T00:00:00Z"
                ),
                org.assertj.core.groups.Tuple.tuple(
                        "row-b", "video-a", 1, "next sanitized transcript text", "2026-01-01T00:00:01Z"
                )
        );
        mockServer.verify();
    }

    @Test
    void translatesFastApiHttpFailuresToTheExistingIntegrationException() {
        UUID processingRequestId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        mockServer.expect(once(), requestTo(
                        "http://localhost:8000/internal/processing-requests/" + processingRequestId
                                + "/transcript-rows"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getTranscriptArtifactRows(processingRequestId.toString()))
                .isInstanceOf(FastApiIntegrationException.class)
                .hasMessage("FastAPI returned HTTP 503 while trying to read transcript artifact rows");
        mockServer.verify();
    }
}
