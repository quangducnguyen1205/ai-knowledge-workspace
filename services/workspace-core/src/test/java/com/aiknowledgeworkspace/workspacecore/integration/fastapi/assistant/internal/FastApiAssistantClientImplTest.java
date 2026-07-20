package com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.common.FastApiIntegrationException;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.adapter.out.provider.common.InvalidFastApiResponseException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FastApiAssistantClientImplTest {

    private MockRestServiceServer mockServer;
    private FastApiAssistantClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8000");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        client = new FastApiAssistantClientImpl(builder.build(), "/internal/assistant/answer");
    }

    @Test
    void answerPostsStructuredRequestToInternalAssistantEndpoint() {
        mockServer.expect(once(), requestTo("http://localhost:8000/internal/assistant/answer"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"question\":\"What is DP?\"")))
                .andExpect(content().string(containsString("\"sourceId\":\"src-1\"")))
                .andRespond(withSuccess("""
                        {
                          "answer": "Dynamic programming reuses subproblem results.",
                          "citedSourceIds": ["src-1"],
                          "insufficientContext": false
                        }
                        """, MediaType.APPLICATION_JSON));

        FastApiAssistantAnswerResponse response = client.answer(new FastApiAssistantAnswerRequest(
                "What is DP?",
                List.of(new FastApiAssistantSourceRequest(
                        "src-1",
                        UUID.randomUUID(),
                        "Lecture",
                        "row-1",
                        1,
                        "2026-06-25T00:00:01Z",
                        "source text"
                ))
        ));

        assertThat(response.answer()).isEqualTo("Dynamic programming reuses subproblem results.");
        assertThat(response.citedSourceIds()).containsExactly("src-1");
        assertThat(response.insufficientContext()).isFalse();
        mockServer.verify();
    }

    @Test
    void non2xxResponseThrowsFastApiIntegrationException() {
        mockServer.expect(once(), requestTo("http://localhost:8000/internal/assistant/answer"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Assistant LLM is unavailable\"}"));

        assertThatThrownBy(() -> client.answer(emptyRequest()))
                .isInstanceOf(FastApiIntegrationException.class)
                .hasMessageContaining("HTTP 503")
                .hasMessageContaining("generate assistant answer");
        mockServer.verify();
    }

    @Test
    void malformedContractResponseThrowsInvalidFastApiResponseException() {
        mockServer.expect(once(), requestTo("http://localhost:8000/internal/assistant/answer"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "answer": "missing contract fields"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.answer(emptyRequest()))
                .isInstanceOf(InvalidFastApiResponseException.class)
                .hasMessageContaining("citedSourceIds");
        mockServer.verify();
    }

    private FastApiAssistantAnswerRequest emptyRequest() {
        return new FastApiAssistantAnswerRequest("question", List.of());
    }
}
