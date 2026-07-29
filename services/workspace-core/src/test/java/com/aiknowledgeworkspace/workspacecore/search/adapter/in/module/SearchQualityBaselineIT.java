package com.aiknowledgeworkspace.workspacecore.search.adapter.in.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.assistant.application.port.out.AssistantSearchPage;
import com.aiknowledgeworkspace.workspacecore.search.adapter.out.search.ElasticsearchClientConfig;
import com.aiknowledgeworkspace.workspacecore.search.adapter.out.search.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.search.adapter.out.search.ElasticsearchTranscriptAdapter;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.SearchIndexOperationException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetDetails;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetUnavailableException;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.TranscriptIndexWriteOperation;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchHit;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchQuery;
import com.aiknowledgeworkspace.workspacecore.search.application.query.SearchResult;
import com.aiknowledgeworkspace.workspacecore.search.application.service.SearchApplicationService;
import com.aiknowledgeworkspace.workspacecore.search.application.service.TranscriptIndexDocumentMapper;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class SearchQualityBaselineIT {

    private static final String CORPUS_RESOURCE = "/search-quality/v1/corpus.json";
    private static final String EXPECTED_RESOURCE = "/search-quality/v1/expected-baseline.json";
    private static final String INDEX_NAME = "search-quality-v1";
    private static final String UNICODE_FIDELITY_INDEX_NAME = "search-quality-unicode-fidelity";
    private static final String BULK_FAILURE_INDEX_NAME = "search-quality-bulk-failure";
    private static final String ELASTICSEARCH_IMAGE =
            "docker.elastic.co/elasticsearch/elasticsearch:8.11.1";
    private static final int EXPECTED_DOCUMENT_COUNT = 102;
    private static final UUID DEFAULT_WORKSPACE_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VIETNAMESE_ASSET_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000009");
    private static final String VIETNAMESE_ROW_ID = "vi-accented";
    private static final String VIETNAMESE_TEXT =
            "Thuật toán tìm kiếm nhị phân giảm một nửa không gian sau mỗi bước.";

    @Container
    private static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer(DockerImageName.parse(ELASTICSEARCH_IMAGE))
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("xpack.security.http.ssl.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .withStartupTimeout(Duration.ofMinutes(3));

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static RestClient restClient;
    private static ElasticsearchTranscriptAdapter elasticsearchAdapter;
    private static SearchApplicationService searchApplicationService;
    private static AssistantSearchPortAdapter assistantSearchAdapter;
    private static Corpus corpus;
    private static ExpectedBaseline expectedBaseline;

    @BeforeAll
    static void setUpBaseline() throws IOException {
        corpus = readResource(CORPUS_RESOURCE, Corpus.class);
        expectedBaseline = readResource(EXPECTED_RESOURCE, ExpectedBaseline.class);
        assertThat(expectedBaseline.version()).isEqualTo(corpus.version());

        String elasticsearchBaseUrl = "http://" + ELASTICSEARCH.getHttpHostAddress();
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl(elasticsearchBaseUrl);
        properties.setTranscriptIndexName(INDEX_NAME);
        restClient = ReflectionTestUtils.invokeMethod(
                new ElasticsearchClientConfig(),
                "elasticsearchRestClient",
                properties
        );
        assertThat(restClient).isNotNull();
        elasticsearchAdapter = new ElasticsearchTranscriptAdapter(restClient, properties, OBJECT_MAPPER);

        elasticsearchAdapter.ensureTranscriptIndexExists();
        elasticsearchAdapter.indexTranscriptRows(indexOperations(corpus));
        elasticsearchAdapter.refreshTranscriptIndex();

        searchApplicationService = new SearchApplicationService(
                new CorpusWorkspaceAccess(corpus),
                new CorpusAssetQueryPort(corpus),
                elasticsearchAdapter
        );
        assistantSearchAdapter = new AssistantSearchPortAdapter(searchApplicationService);
    }

    @AfterAll
    static void removeEvaluationIndex() {
        if (restClient != null && ELASTICSEARCH.isRunning()) {
            restClient.delete()
                    .uri("/{indexName}", INDEX_NAME)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    @Test
    void disposableElasticsearchIsHealthyAndProductionMappingContainsCanonicalFields() {
        assertThat(ELASTICSEARCH.isRunning()).isTrue();

        JsonNode health = restClient.get()
                .uri("/_cluster/health?wait_for_status=yellow&timeout=30s")
                .retrieve()
                .body(JsonNode.class);
        assertThat(health).isNotNull();
        assertThat(health.path("timed_out").asBoolean()).isFalse();
        assertThat(health.path("status").asText()).isIn("yellow", "green");

        JsonNode mapping = restClient.get()
                .uri("/{indexName}/_mapping", INDEX_NAME)
                .retrieve()
                .body(JsonNode.class);
        assertThat(mapping).isNotNull();
        JsonNode properties = mapping.path(INDEX_NAME).path("mappings").path("properties");
        assertThat(properties.path("assetId").path("type").asText()).isEqualTo("text");
        assertThat(properties.path("workspaceId").path("fields").path("keyword").path("type").asText())
                .isEqualTo("keyword");
        assertThat(properties.path("transcriptRowId").path("fields").path("keyword").path("type").asText())
                .isEqualTo("keyword");
        assertThat(properties.path("segmentIndex").path("type").asText()).isEqualTo("integer");
        assertThat(properties.path("startMs").path("type").asText()).isEqualTo("long");
        assertThat(properties.path("endMs").path("type").asText()).isEqualTo("long");
        assertThat(properties.path("text").path("type").asText()).isEqualTo("text");

        JsonNode count = restClient.get()
                .uri("/{indexName}/_count", INDEX_NAME)
                .retrieve()
                .body(JsonNode.class);
        assertThat(count).isNotNull();
        assertThat(count.path("count").asInt()).isEqualTo(EXPECTED_DOCUMENT_COUNT);
    }

    @Test
    void fullCorpusUsesProductionBulkAndPreservesAccentedVietnameseIdentityTimingAndRank() {
        String documentId = VIETNAMESE_ASSET_ID + "-" + VIETNAMESE_ROW_ID;
        JsonNode storedDocument = restClient.get()
                .uri("/{indexName}/_doc/{documentId}", INDEX_NAME, documentId)
                .retrieve()
                .body(JsonNode.class);

        assertThat(storedDocument).isNotNull();
        assertThat(storedDocument.path("found").asBoolean()).isTrue();
        assertThat(storedDocument.path("_id").asText()).isEqualTo(documentId);
        JsonNode source = storedDocument.path("_source");
        assertThat(source.path("transcriptRowId").asText()).isEqualTo(VIETNAMESE_ROW_ID);
        assertThat(source.path("text").asText()).isEqualTo(VIETNAMESE_TEXT);
        assertThat(source.path("startMs").asLong()).isEqualTo(240000L);
        assertThat(source.path("endMs").asLong()).isEqualTo(244000L);

        SearchResult result = search("thuật toán tìm kiếm", DEFAULT_WORKSPACE_ID, null);
        assertThat(result.hits()).isNotEmpty();
        SearchHit first = result.hits().getFirst();
        assertThat(first.transcriptRowId()).isEqualTo(VIETNAMESE_ROW_ID);
        assertThat(first.text()).isEqualTo(VIETNAMESE_TEXT);
        assertThat(first.startMs()).isEqualTo(240000L);
        assertThat(first.endMs()).isEqualTo(244000L);
    }

    @Test
    void mixedAsciiAndUnicodeBatchRoundTripsThroughProductionMapperAndBulkAdapter() {
        ElasticsearchTranscriptAdapter unicodeAdapter = newAdapter(UNICODE_FIDELITY_INDEX_NAME);
        List<IndexingTranscriptRow> rows = List.of(
                unicodeRow("ascii", 0, "ASCII transcript"),
                unicodeRow("vietnamese", 1, VIETNAMESE_TEXT),
                unicodeRow("decomposed", 2, "Cafe\u0301 uses a decomposed combining accent."),
                unicodeRow("cjk", 3, "二分探索は検索範囲を半分にします。"),
                unicodeRow("emoji", 4, "Search checkpoint 🔎✅"),
                unicodeRow(
                        "json-sensitive",
                        5,
                        "Unicode “quoted” text with \\\\path and a logical line break:\nsecond line."
                )
        );
        IndexingAssetSource source = new IndexingAssetSource(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                DEFAULT_WORKSPACE_ID,
                "Unicode mixed batch",
                rows
        );
        List<TranscriptIndexWriteOperation> operations = indexOperations(source);
        boolean indexCreated = false;

        try {
            unicodeAdapter.ensureTranscriptIndexExists();
            indexCreated = true;
            unicodeAdapter.indexTranscriptRows(operations);
            unicodeAdapter.refreshTranscriptIndex();

            JsonNode count = restClient.get()
                    .uri("/{indexName}/_count", UNICODE_FIDELITY_INDEX_NAME)
                    .retrieve()
                    .body(JsonNode.class);
            assertThat(count).isNotNull();
            assertThat(count.path("count").asInt()).isEqualTo(operations.size());

            for (TranscriptIndexWriteOperation operation : operations) {
                JsonNode storedDocument = restClient.get()
                        .uri(
                                "/{indexName}/_doc/{documentId}",
                                UNICODE_FIDELITY_INDEX_NAME,
                                operation.documentId()
                        )
                        .retrieve()
                        .body(JsonNode.class);
                assertThat(storedDocument).isNotNull();
                assertThat(storedDocument.path("_id").asText()).isEqualTo(operation.documentId());
                assertThat(storedDocument.path("_source").path("transcriptRowId").asText())
                        .isEqualTo(operation.document().transcriptRowId());
                assertThat(storedDocument.path("_source").path("text").asText())
                        .isEqualTo(operation.document().text());
            }
        } finally {
            if (indexCreated) {
                restClient.delete()
                        .uri("/{indexName}", UNICODE_FIDELITY_INDEX_NAME)
                        .retrieve()
                        .toBodilessEntity();
            }
        }
    }

    @Test
    void realElasticsearchBulkItemFailureStillRaisesBoundedOperationError() {
        String privateTranscriptText = "this text must not appear in the integration exception";
        ElasticsearchTranscriptAdapter failureAdapter = newAdapter(BULK_FAILURE_INDEX_NAME);
        TranscriptIndexWriteOperation rejected = new TranscriptIndexWriteOperation(
                "rejected-document",
                new TranscriptIndexDocumentMapper().toDocument(
                        new IndexingAssetSource(
                                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                                DEFAULT_WORKSPACE_ID,
                                "Rejected operation",
                                List.of()
                        ),
                        unicodeRow("rejected", 0, privateTranscriptText)
                )
        );

        restClient.put()
                .uri("/{indexName}", BULK_FAILURE_INDEX_NAME)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "settings", Map.of("number_of_shards", 1, "number_of_replicas", 0),
                        "mappings", Map.of(
                                "properties", Map.of("startMs", Map.of("type", "ip"))
                        )
                ))
                .retrieve()
                .toBodilessEntity();
        try {
            assertThatThrownBy(() -> failureAdapter.indexTranscriptRows(List.of(rejected)))
                    .isInstanceOf(SearchIndexOperationException.class)
                    .hasMessageContaining("with status 400")
                    .hasMessageNotContaining(privateTranscriptText);
        } finally {
            restClient.delete()
                    .uri("/{indexName}", BULK_FAILURE_INDEX_NAME)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    @TestFactory
    Stream<DynamicTest> versionedScenariosMatchMeasuredBaseline() {
        return expectedBaseline.scenarios().stream()
                .map(scenario -> DynamicTest.dynamicTest(
                        scenario.id() + " [" + scenario.classification() + "]",
                        () -> assertScenarioMatchesBaseline(scenario)
                ));
    }

    @Test
    void repeatedEqualScoreQueryKeepsStableOrderedCanonicalRowIds() {
        ExpectedScenario scenario = scenario("deterministic-ties");
        List<String> expectedOrder = evaluate(scenario).orderedRowIds();

        for (int repetition = 0; repetition < 10; repetition++) {
            assertThat(evaluate(scenario).orderedRowIds())
                    .as("repeat %s must keep deterministic Elasticsearch and Java tie-breaking", repetition)
                    .containsExactlyElementsOf(expectedOrder);
        }
    }

    @Test
    void canonicalIdentityTimingAndLegacyNullTimingSurviveProductionParsing() {
        SearchResult exactPhrase = search("vector clocks", DEFAULT_WORKSPACE_ID, null);
        SearchHit exactRow = hit(exactPhrase, "vector-exact");
        assertThat(exactRow.assetId())
                .isEqualTo(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001"));
        assertThat(exactRow.segmentIndex()).isZero();
        assertThat(exactRow.startMs()).isZero();
        assertThat(exactRow.endMs()).isEqualTo(3000L);

        SearchResult oneCharacter = search("x", DEFAULT_WORKSPACE_ID, null);
        SearchHit legacyNullTimingRow = hit(oneCharacter, "short-one-character");
        assertThat(legacyNullTimingRow.startMs()).isNull();
        assertThat(legacyNullTimingRow.endMs()).isNull();
    }

    @Test
    void workspaceAndOptionalAssetScopesExcludeOtherDocuments() {
        SearchResult isolated = search("isolation beacon", DEFAULT_WORKSPACE_ID, null);
        assertThat(isolated.hits())
                .extracting(SearchHit::transcriptRowId)
                .containsExactly("isolation-local")
                .doesNotContain("isolation-foreign");

        UUID selectedAsset = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");
        SearchResult assetScoped = search("vector clocks", DEFAULT_WORKSPACE_ID, selectedAsset);
        assertThat(assetScoped.assetIdFilter()).isEqualTo(selectedAsset);
        assertThat(assetScoped.hits()).isNotEmpty().allMatch(hit -> selectedAsset.equals(hit.assetId()));
    }

    @Test
    void publicAndWorkspacePerAssetCapsRemainCharacterized() {
        Evaluation evaluation = evaluate(scenario("public-and-per-asset-caps"));

        assertThat(evaluation.result().hits()).hasSize(12);
        assertThat(evaluation.result().hits().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        SearchHit::assetId,
                        java.util.stream.Collectors.counting()
                )))
                .allSatisfy((assetId, count) -> assertThat(count)
                        .as("Asset %s must not exceed the workspace-wide cap", assetId)
                        .isLessThanOrEqualTo(3));
    }

    @Test
    void assistantAdapterUsesTheSameProductionSearchUseCaseAndOrdering() {
        SearchResult searchResult = search("vector clocks", DEFAULT_WORKSPACE_ID, null);
        AssistantSearchPage assistantResult =
                assistantSearchAdapter.search("vector clocks", DEFAULT_WORKSPACE_ID, null);

        assertThat(assistantResult.workspaceIdFilter()).isEqualTo(searchResult.workspaceIdFilter());
        assertThat(assistantResult.results())
                .extracting(result -> result.transcriptRowId())
                .containsExactlyElementsOf(searchResult.hits().stream().map(SearchHit::transcriptRowId).toList());
        assertThat(assistantResult.results())
                .extracting(result -> result.startMs())
                .containsExactlyElementsOf(searchResult.hits().stream().map(SearchHit::startMs).toList());
    }

    private static void assertScenarioMatchesBaseline(ExpectedScenario scenario) {
        Evaluation actual = evaluate(scenario);

        assertThat(actual.orderedRowIds())
                .as("%s: %s", scenario.id(), scenario.notes())
                .containsExactlyElementsOf(scenario.orderedRowIds());
        assertThat(actual.orderedAssetIds())
                .as("%s Asset order", scenario.id())
                .containsExactlyElementsOf(scenario.orderedAssetIds());
        assertThat(actual.adjacentClusterCount())
                .as("%s adjacent-cluster count", scenario.id())
                .isEqualTo(scenario.adjacentClusterCount());
        assertThat(actual.distinctAssetCount())
                .as("%s distinct Asset count", scenario.id())
                .isEqualTo(scenario.distinctAssetCount());
        assertThat(actual.targetInTop1()).isEqualTo(scenario.targetInTop1());
        assertThat(actual.targetInTop3()).isEqualTo(scenario.targetInTop3());
        assertThat(actual.targetInTop6()).isEqualTo(scenario.targetInTop6());
    }

    private static Evaluation evaluate(ExpectedScenario scenario) {
        SearchResult result = search(scenario.query(), scenario.workspaceId(), scenario.assetId());
        List<String> orderedRowIds = result.hits().stream().map(SearchHit::transcriptRowId).toList();
        List<UUID> orderedAssetIds = result.hits().stream().map(SearchHit::assetId).toList();
        int targetIndex = orderedRowIds.indexOf(scenario.targetRowId());
        int targetRank = targetIndex < 0 ? Integer.MAX_VALUE : targetIndex + 1;
        return new Evaluation(
                result,
                orderedRowIds,
                orderedAssetIds,
                adjacentClusterCount(result.hits()),
                new HashSet<>(orderedAssetIds).size(),
                targetRank <= 1,
                targetRank <= 3,
                targetRank <= 6
        );
    }

    private static SearchResult search(String query, UUID workspaceId, UUID assetId) {
        return searchApplicationService.search(new SearchQuery(query, workspaceId, assetId));
    }

    private static SearchHit hit(SearchResult result, String transcriptRowId) {
        return result.hits().stream()
                .filter(candidate -> transcriptRowId.equals(candidate.transcriptRowId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing transcript row " + transcriptRowId));
    }

    private static int adjacentClusterCount(List<SearchHit> hits) {
        Map<UUID, List<Integer>> segmentIndexesByAsset = new LinkedHashMap<>();
        hits.forEach(hit -> {
            if (hit.segmentIndex() != null) {
                segmentIndexesByAsset.computeIfAbsent(hit.assetId(), ignored -> new ArrayList<>())
                        .add(hit.segmentIndex());
            }
        });

        int clusterCount = 0;
        for (List<Integer> segmentIndexes : segmentIndexesByAsset.values()) {
            List<Integer> ordered = segmentIndexes.stream().distinct().sorted().toList();
            int runLength = 1;
            for (int index = 1; index < ordered.size(); index++) {
                if (ordered.get(index) == ordered.get(index - 1) + 1) {
                    runLength++;
                } else {
                    if (runLength > 1) {
                        clusterCount++;
                    }
                    runLength = 1;
                }
            }
            if (runLength > 1) {
                clusterCount++;
            }
        }
        return clusterCount;
    }

    private static List<TranscriptIndexWriteOperation> indexOperations(Corpus sourceCorpus) {
        TranscriptIndexDocumentMapper mapper = new TranscriptIndexDocumentMapper();
        List<TranscriptIndexWriteOperation> operations = new ArrayList<>();

        for (CorpusAsset asset : sourceCorpus.assets()) {
            List<IndexingTranscriptRow> rows = expandRows(asset);
            IndexingAssetSource source = new IndexingAssetSource(
                    asset.id(), asset.workspaceId(), asset.title(), rows
            );
            for (IndexingTranscriptRow row : rows) {
                operations.add(new TranscriptIndexWriteOperation(
                        asset.id() + "-" + row.id(),
                        mapper.toDocument(source, row)
                ));
            }
        }
        return List.copyOf(operations);
    }

    private static List<TranscriptIndexWriteOperation> indexOperations(IndexingAssetSource source) {
        TranscriptIndexDocumentMapper mapper = new TranscriptIndexDocumentMapper();
        return source.transcriptRows().stream()
                .map(row -> new TranscriptIndexWriteOperation(
                        source.assetId() + "-" + row.id(),
                        mapper.toDocument(source, row)
                ))
                .toList();
    }

    private static IndexingTranscriptRow unicodeRow(String id, int segmentIndex, String text) {
        long startMs = segmentIndex * 1000L;
        return new IndexingTranscriptRow(
                id,
                "unicode-fixture",
                segmentIndex,
                startMs,
                startMs + 999L,
                text,
                "2026-07-29T00:00:00Z"
        );
    }

    private static ElasticsearchTranscriptAdapter newAdapter(String indexName) {
        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl("http://" + ELASTICSEARCH.getHttpHostAddress());
        properties.setTranscriptIndexName(indexName);
        return new ElasticsearchTranscriptAdapter(restClient, properties, OBJECT_MAPPER);
    }

    private static List<IndexingTranscriptRow> expandRows(CorpusAsset asset) {
        List<IndexingTranscriptRow> rows = new ArrayList<>();
        for (CorpusRow row : asset.rows()) {
            rows.add(new IndexingTranscriptRow(
                    row.id(),
                    "fixture-video-" + asset.id(),
                    row.segmentIndex(),
                    row.startMs(),
                    row.endMs(),
                    row.text(),
                    row.createdAt()
            ));
        }
        for (CorpusSeries series : asset.series()) {
            for (int index = 0; index < series.count(); index++) {
                long startMs = series.startMsStart() + (series.stepMs() * index);
                rows.add(new IndexingTranscriptRow(
                        series.idPrefix() + String.format(Locale.ROOT, "%03d", index),
                        "fixture-video-" + asset.id(),
                        series.segmentIndexStart() + index,
                        startMs,
                        startMs + series.durationMs(),
                        series.text(),
                        series.createdAt()
                ));
            }
        }
        return rows.stream()
                .sorted(Comparator.comparing(IndexingTranscriptRow::segmentIndex))
                .toList();
    }

    private static ExpectedScenario scenario(String id) {
        return expectedBaseline.scenarios().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing expected scenario " + id));
    }

    private static <T> T readResource(String path, Class<T> type) throws IOException {
        try (InputStream input = SearchQualityBaselineIT.class.getResourceAsStream(path)) {
            return OBJECT_MAPPER.readValue(
                    Objects.requireNonNull(input, "Missing test resource " + path),
                    type
            );
        }
    }

    private record Corpus(
            String version,
            List<CorpusWorkspace> workspaces,
            List<CorpusAsset> assets
    ) {
    }

    private record CorpusWorkspace(UUID id, String label) {
    }

    private record CorpusAsset(
            UUID id,
            UUID workspaceId,
            String title,
            List<CorpusRow> rows,
            List<CorpusSeries> series
    ) {
    }

    private record CorpusRow(
            String id,
            Integer segmentIndex,
            Long startMs,
            Long endMs,
            String text,
            String createdAt
    ) {
    }

    private record CorpusSeries(
            String idPrefix,
            int count,
            int segmentIndexStart,
            long startMsStart,
            long stepMs,
            long durationMs,
            String text,
            String createdAt
    ) {
    }

    private record ExpectedBaseline(String version, List<ExpectedScenario> scenarios) {
    }

    private record ExpectedScenario(
            String id,
            String classification,
            String query,
            UUID workspaceId,
            UUID assetId,
            String targetRowId,
            List<String> orderedRowIds,
            List<UUID> orderedAssetIds,
            int adjacentClusterCount,
            int distinctAssetCount,
            boolean targetInTop1,
            boolean targetInTop3,
            boolean targetInTop6,
            String notes
    ) {
    }

    private record Evaluation(
            SearchResult result,
            List<String> orderedRowIds,
            List<UUID> orderedAssetIds,
            int adjacentClusterCount,
            int distinctAssetCount,
            boolean targetInTop1,
            boolean targetInTop3,
            boolean targetInTop6
    ) {
    }

    private static final class CorpusWorkspaceAccess implements WorkspaceAccessUseCase {
        private final Set<UUID> workspaceIds;

        private CorpusWorkspaceAccess(Corpus sourceCorpus) {
            this.workspaceIds = sourceCorpus.workspaces().stream()
                    .map(CorpusWorkspace::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        @Override
        public WorkspaceAccess resolveWorkspaceOrDefault(UUID requestedWorkspaceId) {
            return new WorkspaceAccess(resolveWorkspaceId(requestedWorkspaceId), "search-quality-owner");
        }

        @Override
        public UUID resolveWorkspaceId(UUID requestedWorkspaceId) {
            UUID resolved = requestedWorkspaceId == null ? DEFAULT_WORKSPACE_ID : requestedWorkspaceId;
            if (!workspaceIds.contains(resolved)) {
                throw new IllegalArgumentException("Unknown fixture Workspace " + resolved);
            }
            return resolved;
        }

        @Override
        public boolean isOwnedByCurrentUser(UUID workspaceId) {
            return workspaceIds.contains(workspaceId);
        }
    }

    private static final class CorpusAssetQueryPort implements SearchAssetQueryPort {
        private final Map<UUID, CorpusAsset> assetsById;

        private CorpusAssetQueryPort(Corpus sourceCorpus) {
            this.assetsById = sourceCorpus.assets().stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(CorpusAsset::id, asset -> asset));
        }

        @Override
        public SearchAssetDetails getAuthorizedAssetDetails(UUID assetId) {
            CorpusAsset asset = assetsById.get(assetId);
            if (asset == null) {
                throw new SearchAssetUnavailableException(
                        new IllegalArgumentException("Unknown fixture Asset " + assetId)
                );
            }
            return new SearchAssetDetails(asset.id(), asset.workspaceId(), true);
        }

        @Override
        public List<UUID> findSearchableAssetIdsInWorkspace(UUID workspaceId) {
            return assetsById.values().stream()
                    .filter(asset -> asset.workspaceId().equals(workspaceId))
                    .map(CorpusAsset::id)
                    .sorted()
                    .toList();
        }
    }
}
