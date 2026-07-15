package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.indexing.application.AssetSearchIndexExecutionResult;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.ExecuteIndexJobApplicationService;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.TranscriptIndexDocumentMapper;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.TranscriptSnapshotFingerprintService;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJob;
import com.aiknowledgeworkspace.workspacecore.search.indexing.domain.AssetSearchIndexJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.indexing.application.port.out.TranscriptIndexDocument;
import com.aiknowledgeworkspace.workspacecore.search.indexing.infrastructure.persistence.AssetSearchIndexJobRepository;
import com.aiknowledgeworkspace.workspacecore.search.indexing.transaction.IndexingAttemptTransactionService;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.SearchIndexOperationException;
import com.aiknowledgeworkspace.workspacecore.search.infrastructure.elasticsearch.TranscriptSearchIndexClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
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

import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetSource;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingAssetPort;
import com.aiknowledgeworkspace.workspacecore.search.application.IndexingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.search.infrastructure.elasticsearch.ElasticsearchProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ExecuteIndexJobApplicationServiceTest {

    @Mock
    private AssetSearchIndexJobRepository searchIndexJobRepository;

    @Mock
    private IndexingAssetPort indexingAssetPort;

    private MockRestServiceServer mockServer;
    private ExecuteIndexJobApplicationService executor;

    @BeforeEach
    void setUp() {
        executor = executorWithMapper(new TranscriptIndexDocumentMapper());
    }

    private ExecuteIndexJobApplicationService executorWithMapper(TranscriptIndexDocumentMapper documentMapper) {
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
        return new ExecuteIndexJobApplicationService(
                new IndexingAttemptTransactionService(
                        searchIndexJobRepository,
                        indexingAssetPort,
                        new TranscriptSnapshotFingerprintService(),
                        transactionManager()
                ),
                searchIndexClient,
                documentMapper
        );
    }

    @Test
    void indexJobDeletesStaleDocsIndexesSnapshotAndMarksAssetSearchable() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        IndexingAssetSource indexingSource = source(assetId, workspaceId, "Lecture 1", List.of(
                transcriptRow("row-1", 0, "Binary search tree overview"),
                transcriptRow("row-2", 1, "Traversal example")
        ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(indexingAssetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(indexingSource));

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

        AssetSearchIndexExecutionResult result = executor.execute(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(result.indexedDocumentCount()).isEqualTo(2);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        verify(indexingAssetPort).markSearchable(assetId);
        mockServer.verify();
    }

    @Test
    void missingIndexIsCreatedBeforeReplacingAssetDocuments() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        IndexingAssetSource indexingSource = source(assetId, workspaceId, "Lecture fresh cluster", List.of(
                transcriptRow("row-1", 0, "Fresh index text")
        ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(indexingAssetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(indexingSource));

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

        AssetSearchIndexExecutionResult result = executor.execute(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        verify(indexingAssetPort).markSearchable(assetId);
        mockServer.verify();
    }

    @Test
    void alreadyIndexedJobIsIdempotentNoOpWithoutWritingElasticsearch() {
        UUID assetId = UUID.randomUUID();
        List<IndexingTranscriptRow> transcriptRows = List.of(transcriptRow("row-1", 0, "Indexed text"));
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);
        indexingJob.markIndexing();
        indexingJob.markIndexed(java.time.Instant.now());

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));

        AssetSearchIndexExecutionResult result = executor.execute(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(result.indexedDocumentCount()).isZero();
        verify(indexingAssetPort, never()).findCurrentIndexingSource(assetId);
        verify(indexingAssetPort, never()).markSearchable(assetId);
        mockServer.verify();
    }

    @Test
    void staleSnapshotFingerprintSupersedesJobWithoutWritingElasticsearch() {
        UUID assetId = UUID.randomUUID();
        IndexingAssetSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture 2", List.of(
                transcriptRow("row-1", 0, "New text")
        ));
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "old-fingerprint");

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(indexingAssetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(indexingSource));

        AssetSearchIndexExecutionResult result = executor.execute(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        verify(indexingAssetPort, never()).markSearchable(assetId);
        mockServer.verify();
    }

    @Test
    void snapshotChangeAfterElasticsearchWriteSupersedesJobWithoutMarkingAssetSearchable() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        IndexingAssetSource originalSource = source(assetId, workspaceId, "Lecture race", List.of(
                transcriptRow("row-1", 0, "Original text")
        ));
        IndexingAssetSource changedSource = source(assetId, workspaceId, "Lecture race", List.of(
                transcriptRow("row-1", 0, "Changed text")
        ));
        List<IndexingTranscriptRow> originalRows = originalSource.transcriptRows();
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(originalRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(indexingAssetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(originalSource), Optional.of(changedSource));

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

        AssetSearchIndexExecutionResult result = executor.execute(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.SUPERSEDED);
        verify(indexingAssetPort, never()).markSearchable(assetId);
        mockServer.verify();
    }

    @Test
    void emptySnapshotMarksJobFailedWithoutMarkingAssetSearchable() {
        UUID assetId = UUID.randomUUID();
        IndexingAssetSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture 3", List.of());
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, "fingerprint");

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(indexingAssetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(indexingSource));

        AssetSearchIndexExecutionResult result = executor.execute(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.FAILED);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.FAILED);
        assertThat(indexingJob.getLastError())
                .contains("category=INDEXING_SOURCE_INVALID")
                .contains("failureStage=before_bulk")
                .contains("usableRows=0");
        verify(indexingAssetPort, never()).markSearchable(assetId);
        mockServer.verify();
    }

    @Test
    void elasticsearchFailureIsRethrownForListenerRedelivery() {
        UUID assetId = UUID.randomUUID();
        IndexingAssetSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture 4", List.of(
                transcriptRow("row-1", 0, "Text")
        ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(indexingAssetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(indexingSource));

        expectIndexExists();
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_delete_by_query?refresh=true"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> executor.execute(indexingJob.getId()))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessageContaining("Elasticsearch returned HTTP 500");

        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
        assertThat(indexingJob.getLastError())
                .contains("category=ELASTICSEARCH_RESPONSE_INVALID")
                .contains("failureStage=before_bulk")
                .contains("exception=ElasticsearchIntegrationException")
                .contains("usableRows=1")
                .contains("blankRowsAfterFilter=0")
                .contains("segmentIndexes=0")
                .contains("textLengthMin=4")
                .contains("textLengthMax=4")
                .doesNotContain("Elasticsearch returned HTTP 500")
                .doesNotContain(assetId.toString());
        verify(indexingAssetPort, never()).markSearchable(assetId);
        mockServer.verify();
    }

    @Test
    void preBulkFailureRecordsSourceMetadataWithoutDocumentMapping() {
        AtomicInteger documentMappingCalls = new AtomicInteger();
        executor = executorWithMapper(new TranscriptIndexDocumentMapper() {
            @Override
            public TranscriptIndexDocument toDocument(
                    IndexingAssetSource asset,
                    IndexingTranscriptRow transcriptRow
            ) {
                documentMappingCalls.incrementAndGet();
                return super.toDocument(asset, transcriptRow);
            }
        });

        UUID assetId = UUID.randomUUID();
        String unsafeTranscriptRowId = "PRE_BULK_ROW_ID_SHOULD_NOT_PERSIST";
        String unsafeTranscriptText = "PRE_BULK_TRANSCRIPT_TEXT_SHOULD_NOT_PERSIST";
        String unsafeProviderReason = "PRE_BULK_PROVIDER_REASON_SHOULD_NOT_PERSIST";
        IndexingAssetSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture pre-bulk", List.of(
                transcriptRow(unsafeTranscriptRowId, 7, unsafeTranscriptText)
        ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(indexingAssetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(indexingSource));

        expectIndexExists();
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_delete_by_query?refresh=true"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "total": 1,
                          "deleted": 0,
                          "version_conflicts": 0,
                          "failures": [
                            {
                              "cause": {
                                "reason": "%s"
                              }
                            }
                          ]
                        }
                        """.formatted(unsafeProviderReason), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> executor.execute(indexingJob.getId()))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessageContaining(unsafeProviderReason);

        assertThat(documentMappingCalls).hasValue(0);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
        assertThat(indexingJob.getLastError())
                .contains("category=ELASTICSEARCH_RESPONSE_INVALID")
                .contains("failureStage=before_bulk")
                .contains("exception=ElasticsearchIntegrationException")
                .contains("usableRows=1")
                .contains("blankRowsAfterFilter=0")
                .contains("segmentIndexes=7")
                .contains("textLengthMin=" + unsafeTranscriptText.length())
                .contains("textLengthMax=" + unsafeTranscriptText.length())
                .doesNotContain(unsafeTranscriptText)
                .doesNotContain(unsafeTranscriptRowId)
                .doesNotContain(assetId.toString())
                .doesNotContain(unsafeProviderReason);
        verify(indexingAssetPort, never()).markSearchable(assetId);
        mockServer.verify();
    }

    @Test
    void bulkFailureRecordsSafeDiagnosticWithoutRawTextIdsOrProviderMessage() {
        UUID assetId = UUID.randomUUID();
        String unsafeTranscriptRowId = "RAW_SOURCE_ID_SHOULD_NOT_PERSIST";
        String unsafeTranscriptText = "DO_NOT_PERSIST_TRANSCRIPT_TEXT";
        String unsafeProviderReason = "RAW_PROVIDER_REASON_SHOULD_NOT_PERSIST";
        IndexingAssetSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture 5", List.of(
                transcriptRow(unsafeTranscriptRowId, 2, unsafeTranscriptText)
        ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(indexingAssetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(indexingSource));

        expectDelete(assetId);
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString(unsafeTranscriptText)))
                .andRespond(withSuccess("""
                        {
                          "errors": true,
                          "items": [
                            {
                              "index": {
                                "_id": "%s-%s",
                                "status": 400,
                                "error": {
                                  "type": "mapper_parsing_exception",
                                  "reason": "%s"
                                }
                              }
                            }
                          ]
                        }
                        """.formatted(assetId, unsafeTranscriptRowId, unsafeProviderReason), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> executor.execute(indexingJob.getId()))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessageContaining(unsafeProviderReason);

        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
        assertThat(indexingJob.getLastError())
                .contains("category=ELASTICSEARCH_BULK_REJECTED")
                .contains("failureStage=bulk_response")
                .contains("exception=ElasticsearchIntegrationException")
                .contains("usableRows=1")
                .contains("blankRowsAfterFilter=0")
                .contains("segmentIndexes=2")
                .contains("textLengthBuckets=null:0,0:0,1-80:1")
                .doesNotContain(unsafeTranscriptText)
                .doesNotContain(unsafeTranscriptRowId)
                .doesNotContain(assetId.toString())
                .doesNotContain(unsafeProviderReason);
        verify(indexingAssetPort, never()).markSearchable(assetId);
        mockServer.verify();
    }

    @Test
    void diagnosticPersistenceFailureDoesNotMaskOriginalIndexingFailure() {
        UUID assetId = UUID.randomUUID();
        IndexingAssetSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture 6", List.of(
                transcriptRow("row-1", 0, "Text")
        ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(searchIndexJobRepository.save(indexingJob))
                .thenReturn(indexingJob)
                .thenThrow(new IllegalStateException("DIAGNOSTIC_SAVE_SHOULD_NOT_MASK"));
        when(indexingAssetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(indexingSource));

        expectIndexExists();
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_delete_by_query?refresh=true"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> executor.execute(indexingJob.getId()))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessageContaining("Elasticsearch returned HTTP 500")
                .hasMessageNotContaining("DIAGNOSTIC_SAVE_SHOULD_NOT_MASK");

        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
        verify(indexingAssetPort, never()).markSearchable(assetId);
        mockServer.verify();
    }

    @Test
    void diagnosticConstructionDoesNotRepeatIndexDocumentMappingOrMaskOriginalFailure() {
        AtomicInteger documentMappingCalls = new AtomicInteger();
        executor = executorWithMapper(new TranscriptIndexDocumentMapper() {
            @Override
            public TranscriptIndexDocument toDocument(
                    IndexingAssetSource asset,
                    IndexingTranscriptRow transcriptRow
            ) {
                if (documentMappingCalls.incrementAndGet() > 1) {
                    throw new IllegalStateException("SECOND_MAPPING_SHOULD_NOT_MASK");
                }
                return super.toDocument(asset, transcriptRow);
            }
        });

        UUID assetId = UUID.randomUUID();
        String unsafeProviderReason = "ORIGINAL_PROVIDER_REASON_SHOULD_WIN";
        IndexingAssetSource indexingSource = source(assetId, UUID.randomUUID(), "Lecture 7", List.of(
                transcriptRow("row-1", 3, "Mapped once")
        ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(indexingAssetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(indexingSource));

        expectDelete(assetId);
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "errors": true,
                          "items": [
                            {
                              "index": {
                                "_id": "%s-row-1",
                                "status": 400,
                                "error": {
                                  "type": "mapper_parsing_exception",
                                  "reason": "%s"
                                }
                              }
                            }
                          ]
                        }
                        """.formatted(assetId, unsafeProviderReason), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> executor.execute(indexingJob.getId()))
                .isInstanceOf(SearchIndexOperationException.class)
                .hasMessageContaining(unsafeProviderReason)
                .hasMessageNotContaining("SECOND_MAPPING_SHOULD_NOT_MASK");

        assertThat(documentMappingCalls).hasValue(1);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXING);
        assertThat(indexingJob.getLastError())
                .contains("category=ELASTICSEARCH_BULK_REJECTED")
                .contains("segmentIndexes=3")
                .contains("textLengthMin=11")
                .contains("textLengthMax=11")
                .doesNotContain(unsafeProviderReason)
                .doesNotContain("SECOND_MAPPING_SHOULD_NOT_MASK")
                .doesNotContain(assetId.toString());
        verify(indexingAssetPort, never()).markSearchable(assetId);
        mockServer.verify();
    }

    @Test
    void interruptedIndexingJobCanBeRedeliveredAndCompleted() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        IndexingAssetSource indexingSource = source(assetId, workspaceId, "Lecture retry", List.of(
                transcriptRow("row-1", 0, "Retry text")
        ));
        List<IndexingTranscriptRow> transcriptRows = indexingSource.transcriptRows();
        String fingerprint = new TranscriptSnapshotFingerprintService().fingerprint(transcriptRows);
        AssetSearchIndexJob indexingJob = new AssetSearchIndexJob(UUID.randomUUID(), assetId, fingerprint);
        indexingJob.markIndexing();

        when(searchIndexJobRepository.findById(indexingJob.getId())).thenReturn(Optional.of(indexingJob));
        when(indexingAssetPort.findCurrentIndexingSource(assetId)).thenReturn(Optional.of(indexingSource));

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

        AssetSearchIndexExecutionResult result = executor.execute(indexingJob.getId());

        assertThat(result.status()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(indexingJob.getStatus()).isEqualTo(AssetSearchIndexJobStatus.INDEXED);
        assertThat(indexingJob.getAttemptCount()).isEqualTo(2);
        verify(indexingAssetPort).markSearchable(assetId);
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

    private IndexingAssetSource source(
            UUID assetId,
            UUID workspaceId,
            String title,
            List<IndexingTranscriptRow> transcriptRows
    ) {
        return new IndexingAssetSource(assetId, workspaceId, title, transcriptRows);
    }

    private IndexingTranscriptRow transcriptRow(
            String transcriptRowId,
            int segmentIndex,
            String text
    ) {
        return new IndexingTranscriptRow(
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
