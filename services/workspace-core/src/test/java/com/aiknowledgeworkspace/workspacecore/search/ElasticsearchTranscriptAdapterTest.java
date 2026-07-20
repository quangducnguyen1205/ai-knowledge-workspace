package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.SearchIndexOperationException;
import com.aiknowledgeworkspace.workspacecore.search.adapter.out.search.ElasticsearchTranscriptAdapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aiknowledgeworkspace.workspacecore.search.adapter.out.search.ElasticsearchProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ElasticsearchTranscriptAdapterTest {

    private MockRestServiceServer mockServer;
    private ElasticsearchTranscriptAdapter client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:9201");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl("http://localhost:9201");
        properties.setTranscriptIndexName("asset-transcript-rows");

        client = new ElasticsearchTranscriptAdapter(builder.build(), properties, new ObjectMapper());
    }

    @Test
    void ensureTranscriptIndexExistsDoesNotCreateExistingIndex() {
        expectIndexExists();

        client.ensureTranscriptIndexExists();

        mockServer.verify();
    }

    @Test
    void ensureTranscriptIndexExistsCreatesMissingIndexWithSearchCompatibleMapping() {
        expectMissingIndex();
        expectCreateIndex();

        client.ensureTranscriptIndexExists();

        mockServer.verify();
    }

    @Test
    void ensureTranscriptIndexExistsTreatsAlreadyExistsCreateRaceAsSafe() {
        expectMissingIndex();
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "type": "resource_already_exists_exception",
                                    "reason": "index [asset-transcript-rows] already exists"
                                  },
                                  "status": 400
                                }
                                """));
        expectIndexExists();

        client.ensureTranscriptIndexExists();

        mockServer.verify();
    }

    @Test
    void ensureTranscriptIndexExistsPropagatesUnrelatedExistenceCheckError() {
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(client::ensureTranscriptIndexExists)
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageContaining("checking transcript index existence");

        mockServer.verify();
    }

    @Test
    void ensureTranscriptIndexExistsPropagatesUnrelatedCreateError() {
        expectMissingIndex();
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "type": "mapper_parsing_exception",
                                    "reason": "mapping conflict"
                                  },
                                  "status": 400
                                }
                                """));

        assertThatThrownBy(client::ensureTranscriptIndexExists)
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("creating transcript index");

        mockServer.verify();
    }

    private void expectIndexExists() {
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withSuccess());
    }

    private void expectMissingIndex() {
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
    }

    private void expectCreateIndex() {
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(containsString("\"number_of_shards\":1")))
                .andExpect(content().string(containsString("\"number_of_replicas\":0")))
                .andExpect(content().string(containsString("\"assetId\"")))
                .andExpect(content().string(containsString("\"workspaceId\"")))
                .andExpect(content().string(containsString("\"assetStatus\"")))
                .andExpect(content().string(containsString("\"fields\":{\"keyword\"")))
                .andExpect(content().string(containsString("\"transcriptRowId\"")))
                .andExpect(content().string(containsString("\"segmentIndex\":{\"type\":\"integer\"}")))
                .andExpect(content().string(containsString("\"text\":{\"type\":\"text\"}")))
                .andRespond(withSuccess());
    }
}
