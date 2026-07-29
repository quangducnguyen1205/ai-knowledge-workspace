package com.aiknowledgeworkspace.workspacecore.search.adapter.out.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.TranscriptIndexDocument;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.TranscriptIndexWriteOperation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ElasticsearchTranscriptAdapterBulkPayloadTest {

    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer mockServer;
    private ElasticsearchTranscriptAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("http://localhost:9201");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl("http://localhost:9201");
        properties.setTranscriptIndexName("asset-transcript-rows");
        adapter = new ElasticsearchTranscriptAdapter(restClientBuilder.build(), properties, objectMapper);
    }

    @Test
    void bulkPayloadIsUtf8NdjsonWithOneMetadataAndDocumentLinePerOperation() throws Exception {
        String unicodeAndJsonSensitiveText =
                "Thuật toán e\u0301 中文 😀 với \"trích dẫn\", \\\\đường dẫn và dòng một\ndòng hai.";
        List<TranscriptIndexWriteOperation> operations = List.of(
                operation("ascii-document", "ascii-row", "ASCII transcript"),
                operation("unicode-document", "unicode-row", unicodeAndJsonSensitiveText)
        );

        byte[] payload = adapter.buildBulkRequestBody(operations);
        String decodedPayload = new String(payload, StandardCharsets.UTF_8);
        String[] lines = decodedPayload.split("\n", -1);

        assertThat(payload[payload.length - 1]).isEqualTo((byte) '\n');
        assertThat(lines).hasSize((operations.size() * 2) + 1);
        assertThat(lines[lines.length - 1]).isEmpty();
        assertThat(payload.length).isGreaterThan(decodedPayload.length());

        for (int operationIndex = 0; operationIndex < operations.size(); operationIndex++) {
            TranscriptIndexWriteOperation operation = operations.get(operationIndex);
            JsonNode metadataLine = objectMapper.readTree(lines[operationIndex * 2]);
            JsonNode documentLine = objectMapper.readTree(lines[(operationIndex * 2) + 1]);

            assertThat(metadataLine.path("index").path("_id").asText())
                    .isEqualTo(operation.documentId());
            assertThat(documentLine.path("transcriptRowId").asText())
                    .isEqualTo(operation.document().transcriptRowId());
            assertThat(documentLine.path("text").asText())
                    .isEqualTo(operation.document().text());
        }

        assertThat(lines[3]).contains("\\n");
    }

    @Test
    void bulkRequestSendsExactUtf8BytesWithNdjsonContentTypeAndByteLength() {
        TranscriptIndexWriteOperation operation = operation(
                "vietnamese-document",
                "vi-accented",
                "Thuật toán tìm kiếm nhị phân giảm một nửa không gian sau mỗi bước."
        );
        byte[] expectedPayload = adapter.buildBulkRequestBody(List.of(operation));

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/x-ndjson"))
                .andExpect(header(HttpHeaders.CONTENT_LENGTH, Integer.toString(expectedPayload.length)))
                .andExpect(content().bytes(expectedPayload))
                .andRespond(withSuccess("""
                        {
                          "errors": false,
                          "items": [
                            {"index": {"_id": "vietnamese-document", "status": 201}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        adapter.indexTranscriptRows(List.of(operation));

        mockServer.verify();
    }

    private TranscriptIndexWriteOperation operation(String documentId, String rowId, String text) {
        return new TranscriptIndexWriteOperation(
                documentId,
                new TranscriptIndexDocument(
                        ASSET_ID,
                        WORKSPACE_ID,
                        "Unicode transport",
                        rowId,
                        0,
                        0L,
                        1000L,
                        text,
                        "2026-07-29T00:00:00Z",
                        "SEARCHABLE"
                )
        );
    }
}
