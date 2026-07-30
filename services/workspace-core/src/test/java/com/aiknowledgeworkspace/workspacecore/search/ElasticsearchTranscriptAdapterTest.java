package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.SearchIndexOperationException;
import com.aiknowledgeworkspace.workspacecore.search.adapter.out.search.ElasticsearchTranscriptAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aiknowledgeworkspace.workspacecore.search.adapter.out.search.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
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
    void ensureTranscriptIndexExistsConvergesTimingMappingForExistingIndex() {
        expectIndexExists();
        expectTimingMappingUpdate();

        client.ensureTranscriptIndexExists();

        mockServer.verify();
    }

    @Test
    void repeatedExistingIndexConvergenceIsIdempotent() {
        expectIndexExists();
        expectTimingMappingUpdate();
        expectIndexExists();
        expectTimingMappingUpdate();

        client.ensureTranscriptIndexExists();
        client.ensureTranscriptIndexExists();

        mockServer.verify();
    }

    @Test
    void legacySearchHitWithoutTimingRemainsReadableAsNull() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"size\":60")))
                .andExpect(content().string(not(containsString("\"collapse\""))))
                .andExpect(content().string(containsString(
                        "\"sort\":[{\"_score\":{\"order\":\"desc\"}},"
                                + "{\"segmentIndex\":{\"order\":\"asc\",\"missing\":\"_last\"}},"
                                + "{\"assetId.keyword\":{\"order\":\"asc\"}},"
                                + "{\"transcriptRowId.keyword\":{\"order\":\"asc\",\"missing\":\"_last\"}}]"
                )))
                .andRespond(withSuccess("""
                        {"hits":{"hits":[{"_score":1.0,"_source":{
                          "assetId":"%s","assetTitle":"Legacy","transcriptRowId":"row-1",
                          "segmentIndex":0,"text":"legacy","createdAt":"2026-07-22T00:00:00Z"
                        }}]}}
                        """.formatted(assetId), MediaType.APPLICATION_JSON));

        var hit = client.search(new TranscriptSearchQuery(
                "legacy", workspaceId, assetId, List.of(assetId), List.of("legacy")
        )).get(0);

        org.assertj.core.api.Assertions.assertThat(hit.startMs()).isNull();
        org.assertj.core.api.Assertions.assertThat(hit.endMs()).isNull();
        mockServer.verify();
    }

    @Test
    void workspaceSearchUsesCollapseAndParsesOnlyCanonicalInnerHits() {
        UUID firstAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID workspaceId = UUID.randomUUID();
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"size\":12")))
                .andExpect(content().string(containsString("\"_source\":false")))
                .andExpect(content().string(containsString(
                        "\"collapse\":{\"field\":\"assetId.keyword\",\"max_concurrent_group_searches\":4"
                )))
                .andExpect(content().string(containsString(
                        "\"inner_hits\":{\"name\":\"asset_moments\",\"size\":3,\"_source\":true"
                )))
                .andExpect(content().string(containsString(
                        "\"inner_hits\":{\"name\":\"asset_moments\",\"size\":3,\"_source\":true,"
                                + "\"sort\":[{\"_score\":{\"order\":\"desc\"}},"
                                + "{\"segmentIndex\":{\"order\":\"asc\",\"missing\":\"_last\"}},"
                                + "{\"transcriptRowId.keyword\":{\"order\":\"asc\",\"missing\":\"_last\"}}]}"
                )))
                .andExpect(content().string(containsString(
                        "\"sort\":[{\"_score\":{\"order\":\"desc\"}},"
                                + "{\"segmentIndex\":{\"order\":\"asc\",\"missing\":\"_last\"}},"
                                + "{\"assetId.keyword\":{\"order\":\"asc\"}},"
                                + "{\"transcriptRowId.keyword\":{\"order\":\"asc\",\"missing\":\"_last\"}}]"
                )))
                .andExpect(content().string(containsString(
                        "\"segmentIndex\":{\"order\":\"asc\",\"missing\":\"_last\"}"
                )))
                .andExpect(content().string(containsString(
                        "\"assetId.keyword\":{\"order\":\"asc\"}"
                )))
                .andExpect(content().string(containsString(
                        "\"transcriptRowId.keyword\":{\"order\":\"asc\",\"missing\":\"_last\"}"
                )))
                .andRespond(withSuccess("""
                        {
                          "hits": {
                            "hits": [
                              {
                                "_score": 9.0,
                                "_source": {
                                  "assetId": "%s",
                                  "transcriptRowId": "outer-duplicate"
                                },
                                "inner_hits": {
                                  "asset_moments": {
                                    "hits": {
                                      "hits": [
                                        {
                                          "_score": 9.0,
                                          "_source": {
                                            "assetId": "%s",
                                            "assetTitle": "First",
                                            "transcriptRowId": "outer-duplicate",
                                            "segmentIndex": 4,
                                            "startMs": 1000,
                                            "endMs": 2000,
                                            "text": "target first",
                                            "createdAt": "2026-07-30T00:00:00Z"
                                          }
                                        },
                                        {
                                          "_score": null,
                                          "_source": {
                                            "assetId": "%s",
                                            "assetTitle": "First",
                                            "transcriptRowId": "nullable",
                                            "segmentIndex": null,
                                            "startMs": null,
                                            "endMs": null,
                                            "text": "target nullable",
                                            "createdAt": "2026-07-30T00:00:01Z"
                                          }
                                        }
                                      ]
                                    }
                                  }
                                }
                              },
                              {
                                "_score": 4.0,
                                "inner_hits": {
                                  "asset_moments": {
                                    "hits": {
                                      "hits": [
                                        {
                                          "_score": 4.0,
                                          "_source": {
                                            "assetId": "%s",
                                            "assetTitle": "Second",
                                            "transcriptRowId": "second-row",
                                            "segmentIndex": 8,
                                            "startMs": 3000,
                                            "endMs": 4000,
                                            "text": "target second",
                                            "createdAt": "2026-07-30T00:00:02Z"
                                          }
                                        }
                                      ]
                                    }
                                  }
                                }
                              }
                            ]
                          }
                        }
                        """.formatted(firstAssetId, firstAssetId, firstAssetId, secondAssetId),
                        MediaType.APPLICATION_JSON));

        var hits = client.search(new TranscriptSearchQuery(
                "target",
                workspaceId,
                null,
                List.of(firstAssetId, secondAssetId),
                List.of("target")
        ));

        assertThat(hits)
                .extracting(hit -> hit.transcriptRowId())
                .containsExactly("outer-duplicate", "nullable", "second-row");
        assertThat(hits.get(0).startMs()).isEqualTo(1000L);
        assertThat(hits.get(1).score()).isNull();
        assertThat(hits.get(1).segmentIndex()).isNull();
        assertThat(hits.get(1).startMs()).isNull();
        assertThat(hits.get(1).endMs()).isNull();
        mockServer.verify();
    }

    @Test
    void groupedSearchRejectsNonNumericScore() {
        UUID assetId = UUID.randomUUID();
        expectGroupedSearchResponse("""
                {"hits":{"hits":[{"inner_hits":{"asset_moments":{"hits":{"hits":[{
                  "_score":"private-score",
                  "_source":{"assetId":"%s","segmentIndex":1}
                }]}}}}]}}
                """.formatted(assetId));

        assertThatThrownBy(() -> client.search(workspaceQuery(assetId)))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessage("Elasticsearch search hit included a non-numeric value for _score")
                .hasMessageNotContaining("private-score");
        mockServer.verify();
    }

    @Test
    void groupedSearchRejectsNonIntegralSegmentIndex() {
        UUID assetId = UUID.randomUUID();
        expectGroupedSearchResponse("""
                {"hits":{"hits":[{"inner_hits":{"asset_moments":{"hits":{"hits":[{
                  "_score":1.0,
                  "_source":{"assetId":"%s","segmentIndex":1.5}
                }]}}}}]}}
                """.formatted(assetId));

        assertThatThrownBy(() -> client.search(workspaceQuery(assetId)))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessageContaining("non-integral or out-of-range value for segmentIndex");
        mockServer.verify();
    }

    @Test
    void groupedSearchRejectsMissingInnerHitStructure() {
        UUID assetId = UUID.randomUUID();
        expectGroupedSearchResponse("{\"hits\":{\"hits\":[{}]}}");

        assertThatThrownBy(() -> client.search(workspaceQuery(assetId)))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessageContaining("did not include asset moment inner hits");
        mockServer.verify();
    }

    @Test
    void groupedSearchRejectsMissingNestedHitsArray() {
        UUID assetId = UUID.randomUUID();
        expectGroupedSearchResponse("""
                {"hits":{"hits":[{"inner_hits":{"asset_moments":{"hits":{}}}}]}}
                """);

        assertThatThrownBy(() -> client.search(workspaceQuery(assetId)))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessageContaining("did not include asset moment inner hits");
        mockServer.verify();
    }

    @Test
    void groupedSearchTreatsMissingScoreAndSegmentIndexAsNull() {
        UUID assetId = UUID.randomUUID();
        expectGroupedSearchResponse("""
                {"hits":{"hits":[{"inner_hits":{"asset_moments":{"hits":{"hits":[{
                  "_source":{"assetId":"%s","transcriptRowId":"missing-nullables"}
                }]}}}}]}}
                """.formatted(assetId));

        var hit = client.search(workspaceQuery(assetId)).getFirst();

        assertThat(hit.score()).isNull();
        assertThat(hit.segmentIndex()).isNull();
        mockServer.verify();
    }

    @Test
    void groupedSearchRejectsEmptyInnerHitGroup() {
        UUID assetId = UUID.randomUUID();
        expectGroupedSearchResponse("""
                {"hits":{"hits":[{"inner_hits":{"asset_moments":{"hits":{"hits":[]}}}}]}}
                """);

        assertThatThrownBy(() -> client.search(workspaceQuery(assetId)))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessageContaining("empty asset moment inner-hit group");
        mockServer.verify();
    }

    @Test
    void groupedSearchRejectsInnerHitWithoutSource() {
        UUID assetId = UUID.randomUUID();
        expectGroupedSearchResponse("""
                {"hits":{"hits":[{"inner_hits":{"asset_moments":{"hits":{"hits":[{"_score":1.0}]}}}}]}}
                """);

        assertThatThrownBy(() -> client.search(workspaceQuery(assetId)))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessage("Elasticsearch search hit did not include _source");
        mockServer.verify();
    }

    @Test
    void groupedSearchRejectsMissingTopLevelHits() {
        UUID assetId = UUID.randomUUID();
        expectGroupedSearchResponse("{\"hits\":{}}");

        assertThatThrownBy(() -> client.search(workspaceQuery(assetId)))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessage("Elasticsearch search response did not include hits");
        mockServer.verify();
    }

    @Test
    void groupedSearchRejectsInvalidAssetUuidWithoutExposingIt() {
        UUID eligibleAssetId = UUID.randomUUID();
        expectGroupedSearchResponse("""
                {"hits":{"hits":[{"inner_hits":{"asset_moments":{"hits":{"hits":[{
                  "_score":1.0,
                  "_source":{"assetId":"private-invalid-asset","segmentIndex":1}
                }]}}}}]}}
                """);

        assertThatThrownBy(() -> client.search(workspaceQuery(eligibleAssetId)))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessage("Elasticsearch search hit included an invalid assetId")
                .hasMessageNotContaining("private-invalid-asset");
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
        expectTimingMappingUpdate();

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
                .andExpect(content().string(containsString("\"startMs\":{\"type\":\"long\"}")))
                .andExpect(content().string(containsString("\"endMs\":{\"type\":\"long\"}")))
                .andExpect(content().string(containsString("\"text\":{\"type\":\"text\"}")))
                .andRespond(withSuccess());
    }

    private void expectTimingMappingUpdate() {
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_mapping"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(containsString("\"startMs\":{\"type\":\"long\"}")))
                .andExpect(content().string(containsString("\"endMs\":{\"type\":\"long\"}")))
                .andRespond(withSuccess());
    }

    private TranscriptSearchQuery workspaceQuery(UUID assetId) {
        return new TranscriptSearchQuery(
                "target",
                UUID.randomUUID(),
                null,
                List.of(assetId),
                List.of("target")
        );
    }

    private void expectGroupedSearchResponse(String responseBody) {
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }
}
