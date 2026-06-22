package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowSnapshotRepository;
import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class AssetSearchIndexingExecutorTest {

    @Mock
    private AssetSearchIndexJobRepository searchIndexJobRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetTranscriptRowSnapshotRepository transcriptRowSnapshotRepository;

    private MockRestServiceServer mockServer;
    private AssetSearchIndexingExecutor executor;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:9201");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl("http://localhost:9201");
        properties.setTranscriptIndexName("asset-transcript-rows");

        TranscriptSearchIndexClient searchIndexClient = new TranscriptSearchIndexClient(
                builder.build(),
                properties,
                new ObjectMapper()
        );
        executor = new AssetSearchIndexingExecutor(
                searchIndexJobRepository,
                assetRepository,
                transcriptRowSnapshotRepository,
                new TranscriptSnapshotFingerprintService(),
                searchIndexClient,
                new TranscriptIndexDocumentMapper(),
                transactionManager()
        );
    }

    @Test
    void indexJobDeletesStaleDocsIndexesSnapshotAndMarksAssetSearchable() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, "Lecture 1", AssetStatus.TRANSCRIPT_READY);
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                transcriptRow(assetId, "row-1", 0, "Binary search tree overview"),
                transcriptRow(assetId, "row-2", 1, "Traversal example")
        );
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(transcriptRowSnapshotRepository.findByAssetId(assetId)).thenReturn(transcriptRows);

        expectDelete(assetId);
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("{\"index\":{\"_id\":\"" + assetId + "-row-1\"}}")))
                .andExpect(content().string(containsString("{\"index\":{\"_id\":\"" + assetId + "-row-2\"}}")))
                .andExpect(content().string(containsString("\"assetTitle\":\"Lecture 1\"")))
                .andExpect(content().string(containsString("\"workspaceId\":\"" + workspaceId + "\"")))
                .andExpect(content().string(containsString("\"assetStatus\":\"SEARCHABLE\"")))
                .andRespond(withSuccess("""
                        {
                          "errors": false,
                          "items": [
                            {"index": {"_id": "%s-row-1", "status": 201}},
                            {"index": {"_id": "%s-row-2", "status": 201}}
                          ]
                        }
                        """.formatted(assetId, assetId), MediaType.APPLICATION_JSON));
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_refresh"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        AssetSearchIndexExecutionResult result = executor.indexJob(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(result.indexedDocumentCount()).isEqualTo(2);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.SEARCHABLE);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        verify(assetRepository).save(asset);
        mockServer.verify();
    }

    @Test
    void missingIndexIsCreatedBeforeReplacingAssetDocuments() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, "Lecture fresh cluster", AssetStatus.TRANSCRIPT_READY);
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                transcriptRow(assetId, "row-1", 0, "Fresh index text")
        );
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(transcriptRowSnapshotRepository.findByAssetId(assetId)).thenReturn(transcriptRows);

        expectMissingIndexCreated();
        expectDeleteRequest(assetId);
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "errors": false,
                          "items": [
                            {"index": {"_id": "%s-row-1", "status": 201}}
                          ]
                        }
                        """.formatted(assetId), MediaType.APPLICATION_JSON));
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_refresh"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        AssetSearchIndexExecutionResult result = executor.indexJob(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.SEARCHABLE);
        mockServer.verify();
    }

    @Test
    void alreadyIndexedJobIsIdempotentNoOpWithoutWritingElasticsearch() {
        UUID assetId = UUID.randomUUID();
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(transcriptRow(assetId, "row-1", 0, "Indexed text"));
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);
        indexingJob.markIndexing();
        indexingJob.markIndexed(java.time.Instant.now());

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));

        AssetSearchIndexExecutionResult result = executor.indexJob(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(result.indexedDocumentCount()).isZero();
        verify(assetRepository, never()).findById(assetId);
        verify(assetRepository, never()).save(any());
        mockServer.verify();
    }

    @Test
    void staleSnapshotFingerprintSupersedesJobWithoutWritingElasticsearch() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, UUID.randomUUID(), "Lecture 2", AssetStatus.TRANSCRIPT_READY);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "old-fingerprint");
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(transcriptRow(assetId, "row-1", 0, "New text"));

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(transcriptRowSnapshotRepository.findByAssetId(assetId)).thenReturn(transcriptRows);

        AssetSearchIndexExecutionResult result = executor.indexJob(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        verify(assetRepository, never()).save(asset);
        mockServer.verify();
    }

    @Test
    void snapshotChangeAfterElasticsearchWriteSupersedesJobWithoutMarkingAssetSearchable() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, "Lecture race", AssetStatus.TRANSCRIPT_READY);
        List<AssetTranscriptRowSnapshot> originalRows = List.of(
                transcriptRow(assetId, "row-1", 0, "Original text")
        );
        List<AssetTranscriptRowSnapshot> changedRows = List.of(
                transcriptRow(assetId, "row-1", 0, "Changed text")
        );
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(originalRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(transcriptRowSnapshotRepository.findByAssetId(assetId)).thenReturn(originalRows, changedRows);

        expectDelete(assetId);
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "errors": false,
                          "items": [
                            {"index": {"_id": "%s-row-1", "status": 201}}
                          ]
                        }
                        """.formatted(assetId), MediaType.APPLICATION_JSON));
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_refresh"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        AssetSearchIndexExecutionResult result = executor.indexJob(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.TRANSCRIPT_READY);
        verify(assetRepository, never()).save(asset);
        mockServer.verify();
    }

    @Test
    void emptySnapshotMarksJobFailedWithoutMarkingAssetSearchable() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, UUID.randomUUID(), "Lecture 3", AssetStatus.TRANSCRIPT_READY);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "fingerprint");

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(transcriptRowSnapshotRepository.findByAssetId(assetId)).thenReturn(List.of());

        AssetSearchIndexExecutionResult result = executor.indexJob(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.FAILED);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.FAILED);
        assertThat(indexingJob.getLastError()).contains("empty or unusable");
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.TRANSCRIPT_READY);
        verify(assetRepository, never()).save(asset);
        mockServer.verify();
    }

    @Test
    void elasticsearchFailureIsRethrownForListenerRedelivery() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, UUID.randomUUID(), "Lecture 4", AssetStatus.TRANSCRIPT_READY);
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(transcriptRow(assetId, "row-1", 0, "Text"));
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(transcriptRowSnapshotRepository.findByAssetId(assetId)).thenReturn(transcriptRows);

        expectIndexExists();
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_delete_by_query?refresh=true"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> executor.indexJob(indexingJob.getId()))
                .isInstanceOf(ElasticsearchIntegrationException.class)
                .hasMessageContaining("Elasticsearch returned HTTP 500");

        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
        verify(assetRepository, never()).save(asset);
        mockServer.verify();
    }

    @Test
    void interruptedIndexingJobCanBeRedeliveredAndCompleted() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, "Lecture retry", AssetStatus.TRANSCRIPT_READY);
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(transcriptRow(assetId, "row-1", 0, "Retry text"));
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);
        indexingJob.markIndexing();

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(asset));
        when(transcriptRowSnapshotRepository.findByAssetId(assetId)).thenReturn(transcriptRows);

        expectDelete(assetId);
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "errors": false,
                          "items": [
                            {"index": {"_id": "%s-row-1", "status": 201}}
                          ]
                        }
                        """.formatted(assetId), MediaType.APPLICATION_JSON));
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_refresh"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        AssetSearchIndexExecutionResult result = executor.indexJob(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(indexingJob.getAttemptCount()).isEqualTo(2);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.SEARCHABLE);
        mockServer.verify();
    }

    private void expectDelete(UUID assetId) {
        expectIndexExists();
        expectDeleteRequest(assetId);
    }

    private void expectIndexExists() {
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withSuccess());
    }

    private void expectMissingIndexCreated() {
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(containsString("\"number_of_shards\":1")))
                .andExpect(content().string(containsString("\"number_of_replicas\":0")))
                .andExpect(content().string(containsString("\"assetId\"")))
                .andExpect(content().string(containsString("\"workspaceId\"")))
                .andExpect(content().string(containsString("\"assetStatus\"")))
                .andExpect(content().string(containsString("\"segmentIndex\":{\"type\":\"integer\"}")))
                .andExpect(content().string(containsString("\"text\":{\"type\":\"text\"}")))
                .andRespond(withSuccess());
    }

    private void expectDeleteRequest(UUID assetId) {
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_delete_by_query?refresh=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"assetId.keyword\":\"" + assetId + "\"")))
                .andRespond(withSuccess("""
                        {
                          "total": 0,
                          "deleted": 0,
                          "version_conflicts": 0,
                          "failures": []
                        }
                        """, MediaType.APPLICATION_JSON));
    }

    private Asset asset(UUID assetId, UUID workspaceId, String title, AssetStatus status) {
        Asset asset = new Asset("lecture.mp4", title, status, new Workspace(workspaceId, "Study Workspace"));
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }

    private AssetTranscriptRowSnapshot transcriptRow(
            UUID assetId,
            String transcriptRowId,
            int segmentIndex,
            String text
    ) {
        return new AssetTranscriptRowSnapshot(
                assetId,
                transcriptRowId,
                "video-1",
                segmentIndex,
                text,
                "2026-03-26T00:00:00Z"
        );
    }

    private PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
