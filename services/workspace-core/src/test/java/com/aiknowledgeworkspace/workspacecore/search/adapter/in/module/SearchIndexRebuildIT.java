package com.aiknowledgeworkspace.workspacecore.search.adapter.in.module;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.search.adapter.out.search.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.indexing.SearchIndexJobStore;
import com.aiknowledgeworkspace.workspacecore.search.application.result.SearchIndexRebuildResult;
import com.aiknowledgeworkspace.workspacecore.search.application.service.ExecuteIndexJobApplicationService;
import com.aiknowledgeworkspace.workspacecore.search.application.service.SearchIndexRebuildService;
import com.aiknowledgeworkspace.workspacecore.search.application.service.TranscriptSnapshotFingerprintService;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.domain.indexing.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The disaster the rebuild exists for, against a real Elasticsearch: PostgreSQL still holds the
 * transcripts, the index is gone, and the newest indexing job still says {@code INDEXED} — the exact
 * state in which every ordinary path concludes there is nothing to do.
 *
 * <p>Correctness is judged by what Elasticsearch actually returns afterwards, not by job status.
 * The container is disposable and created by this test; no developer index is touched.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-rebuild-it;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "integration.elasticsearch.transcript-index-name=asset-transcript-rows-rebuild-it"
})
class SearchIndexRebuildIT {

    private static final String ELASTICSEARCH_IMAGE =
            "docker.elastic.co/elasticsearch/elasticsearch:8.11.1";

    @Container
    private static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer(DockerImageName.parse(ELASTICSEARCH_IMAGE))
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("xpack.security.http.ssl.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void elasticsearchEndpoint(DynamicPropertyRegistry registry) {
        registry.add("integration.elasticsearch.base-url", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
    }

    @Autowired
    private SearchIndexRebuildService searchIndexRebuildService;

    @Autowired
    private ExecuteIndexJobApplicationService executeIndexJobApplicationService;

    @Autowired
    private SearchIndexJobStore searchIndexJobRepository;

    @Autowired
    private CanonicalTranscriptStore canonicalTranscriptStore;

    @Autowired
    private IndexingAssetPort indexingAssetPort;

    @Autowired
    private AssetStore assetRepository;

    @Autowired
    private WorkspaceStore workspaceRepository;

    @Autowired
    private ElasticsearchProperties elasticsearchProperties;

    @Autowired
    @Qualifier("elasticsearchRestClient")
    private RestClient elasticsearchRestClient;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * Each scenario starts from an empty world. Elasticsearch writes cannot roll back with the test
     * transaction, so state is cleared explicitly rather than relying on rollback.
     */
    @BeforeEach
    void resetWorld() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createQuery("delete from AssetSearchIndexJob").executeUpdate();
            entityManager.createQuery("delete from AssetTranscriptRowSnapshot").executeUpdate();
            entityManager.createQuery("delete from Asset").executeUpdate();
            entityManager.createQuery("delete from Workspace").executeUpdate();
        });
        try {
            deleteTranscriptIndex();
        } catch (RuntimeException alreadyAbsent) {
            // Nothing to remove on the first run.
        }
    }

    @Test
    void aDeletedIndexIsRebuiltFromPostgresWhileTheNewestJobStillSaysIndexed() {
        UUID firstAsset = persistAsset("Binary search");
        persistTranscript(firstAsset,
                "Thuật toán tìm kiếm nhị phân giảm một nửa không gian sau mỗi bước.",
                "Điều kiện là dãy đã được sắp xếp.");
        UUID secondAsset = persistAsset("Hash tables");
        persistTranscript(secondAsset, "A hash table trades memory for lookup speed.");

        AssetSearchIndexJob firstHistory = indexNormally(firstAsset);
        AssetSearchIndexJob secondHistory = indexNormally(secondAsset);
        assertThat(documentCount()).isEqualTo(3);

        // The disaster: the projection is gone, PostgreSQL and job history are untouched.
        deleteTranscriptIndex();
        assertThat(indexExists()).isFalse();
        assertThat(reload(firstHistory).getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(reload(secondHistory).getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);

        SearchIndexRebuildResult result = searchIndexRebuildService.rebuildAll();

        assertThat(result.eligible()).isEqualTo(2);
        assertThat(result.indexed()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(indexExists()).as("the rebuild recreated the application index").isTrue();
        assertThat(documentCount()).as("every canonical row is present exactly once").isEqualTo(3);
        assertThat(textsForAsset(firstAsset)).containsExactlyInAnyOrder(
                "Thuật toán tìm kiếm nhị phân giảm một nửa không gian sau mỗi bước.",
                "Điều kiện là dãy đã được sắp xếp.");
        assertThat(textsForAsset(secondAsset))
                .containsExactly("A hash table trades memory for lookup speed.");
        assertThat(searchHitTexts("nhị phân"))
                .as("search returns the canonical transcript again")
                .contains("Thuật toán tìm kiếm nhị phân giảm một nửa không gian sau mỗi bước.");

        // Rerunning converges rather than duplicating.
        SearchIndexRebuildResult second = searchIndexRebuildService.rebuildAll();
        assertThat(second.failed()).isZero();
        assertThat(documentCount()).isEqualTo(3);
    }

    @Test
    void anIndexHoldingStaleDocumentsEndsUpMatchingCanonicalTruth() {
        UUID assetId = persistAsset("Graph traversal");
        persistTranscript(assetId, "Breadth first search visits neighbours first.");
        indexNormally(assetId);

        // A document that canonical PostgreSQL no longer knows about.
        indexRawDocument(assetId + "-orphaned-row", assetId, "A sentence removed from the transcript.");
        assertThat(documentCount()).isEqualTo(2);

        SearchIndexRebuildResult result = searchIndexRebuildService.rebuildAll();

        assertThat(result.indexed()).isEqualTo(1);
        assertThat(textsForAsset(assetId))
                .containsExactly("Breadth first search visits neighbours first.");
        assertThat(documentCount()).isEqualTo(1);
        assertThat(searchHitTexts("removed")).isEmpty();
    }

    // ------------------------------------------------------ Elasticsearch probes

    private boolean indexExists() {
        try {
            elasticsearchRestClient.get()
                    .uri("/{index}", elasticsearchProperties.getTranscriptIndexName())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void deleteTranscriptIndex() {
        elasticsearchRestClient.delete()
                .uri("/{index}", elasticsearchProperties.getTranscriptIndexName())
                .retrieve()
                .toBodilessEntity();
    }

    private int documentCount() {
        refresh();
        JsonNode response = elasticsearchRestClient.get()
                .uri("/{index}/_count", elasticsearchProperties.getTranscriptIndexName())
                .retrieve()
                .body(JsonNode.class);
        return response == null ? 0 : response.path("count").asInt();
    }

    private List<String> textsForAsset(UUID assetId) {
        refresh();
        JsonNode response = elasticsearchRestClient.post()
                .uri("/{index}/_search", elasticsearchProperties.getTranscriptIndexName())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"size": 50, "query": {"term": {"assetId.keyword": "%s"}}}
                        """.formatted(assetId))
                .retrieve()
                .body(JsonNode.class);
        return hitTexts(response);
    }

    private List<String> searchHitTexts(String query) {
        refresh();
        JsonNode response = elasticsearchRestClient.post()
                .uri("/{index}/_search", elasticsearchProperties.getTranscriptIndexName())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"size": 50, "query": {"match": {"text": "%s"}}}
                        """.formatted(query))
                .retrieve()
                .body(JsonNode.class);
        return hitTexts(response);
    }

    private List<String> hitTexts(JsonNode response) {
        List<String> texts = new ArrayList<>();
        if (response != null) {
            response.path("hits").path("hits")
                    .forEach(hit -> texts.add(hit.path("_source").path("text").asText()));
        }
        return texts;
    }

    private void indexRawDocument(String documentId, UUID assetId, String text) {
        elasticsearchRestClient.put()
                .uri("/{index}/_doc/{id}", elasticsearchProperties.getTranscriptIndexName(), documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"assetId": "%s", "text": "%s", "transcriptRowId": "orphaned-row"}
                        """.formatted(assetId, text))
                .retrieve()
                .toBodilessEntity();
    }

    private void refresh() {
        try {
            elasticsearchRestClient.post()
                    .uri("/{index}/_refresh", elasticsearchProperties.getTranscriptIndexName())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // A missing index has nothing to refresh; the assertion that follows reports the truth.
        }
    }

    // ------------------------------------------------------------------ fixtures

    private AssetSearchIndexJob indexNormally(UUID assetId) {
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(
                indexingAssetPort.findCurrentIndexingSource(assetId).orElseThrow().transcriptRows());
        AssetSearchIndexJob job = searchIndexJobRepository.save(new AssetSearchIndexJob(assetId, fingerprint));
        job.attachRequestOutboxEvent(UUID.randomUUID());
        searchIndexJobRepository.save(job);
        executeIndexJobApplicationService.execute(job.getId());
        return job;
    }

    private AssetSearchIndexJob reload(AssetSearchIndexJob job) {
        return searchIndexJobRepository.findById(job.getId()).orElseThrow();
    }

    private UUID persistAsset(String title) {
        Workspace workspace = workspaceRepository.save(
                new Workspace(UUID.randomUUID(), "Algorithms", "user-1", false));
        UUID assetId = UUID.randomUUID();
        assetRepository.save(Asset.uploaded(
                assetId,
                "lecture.mp4",
                title,
                AssetStatus.TRANSCRIPT_READY,
                workspace.getId(),
                "workspace-media",
                "users/user-1/workspaces/%s/assets/%s/raw/lecture.mp4".formatted(workspace.getId(), assetId),
                "video/mp4",
                123L,
                "\"etag-1\""
        ));
        return assetId;
    }

    private void persistTranscript(UUID assetId, String... texts) {
        List<AssetTranscriptRowInput> rows = new ArrayList<>();
        for (int index = 0; index < texts.length; index++) {
            rows.add(new AssetTranscriptRowInput(
                    "row-" + index, "video-1", index, (long) index * 1000, (long) (index + 1) * 1000,
                    texts[index], "2026-09-02T00:00:00Z"
            ));
        }
        canonicalTranscriptStore.replace(assetId, rows);
    }
}
