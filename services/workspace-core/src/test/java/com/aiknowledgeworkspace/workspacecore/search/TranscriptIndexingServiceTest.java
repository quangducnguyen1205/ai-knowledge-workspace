package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aiknowledgeworkspace.workspacecore.asset.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.AssetIndexResponse;
import com.aiknowledgeworkspace.workspacecore.asset.AssetPersistenceService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetService;
import com.aiknowledgeworkspace.workspacecore.asset.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowSnapshot;
import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TranscriptIndexingServiceTest {

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private AssetService assetService;

    @Mock
    private AssetPersistenceService assetPersistenceService;

    private MockRestServiceServer mockServer;
    private TranscriptIndexingService transcriptIndexingService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:9201");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl("http://localhost:9201");
        properties.setTranscriptIndexName("asset-transcript-rows");

        transcriptIndexingService = new TranscriptIndexingService(
                processingJobRepository,
                assetService,
                assetPersistenceService,
                builder.build(),
                properties,
                new TranscriptIndexDocumentMapper(),
                new ObjectMapper()
        );
    }

    @Test
    void indexingSucceedsForTranscriptUsableAsset() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, "Lecture 1", AssetStatus.TRANSCRIPT_READY);
        ProcessingJob processingJob = processingJob(assetId, "task-1", "video-1");
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                transcriptRow(assetId, "row-1", "video-1", 0, "Binary search tree overview"),
                transcriptRow(assetId, "row-2", "video-1", 1, "Traversal example")
        );

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetService.loadUsableTranscriptSnapshot(asset, processingJob)).thenReturn(transcriptRows);

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("{\"index\":{\"_id\":\"" + assetId + "-row-1\"}}")))
                .andExpect(content().string(containsString("{\"index\":{\"_id\":\"" + assetId + "-row-2\"}}")))
                .andExpect(content().string(containsString("\"assetTitle\":\"Lecture 1\"")))
                .andExpect(content().string(containsString("\"transcriptRowId\":\"row-1\"")))
                .andExpect(content().string(containsString("\"transcriptRowId\":\"row-2\"")))
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

        AssetIndexResponse response = transcriptIndexingService.indexAssetTranscript(assetId);

        assertThat(response.assetId()).isEqualTo(assetId);
        assertThat(response.assetStatus()).isEqualTo(AssetStatus.SEARCHABLE);
        assertThat(response.indexedDocumentCount()).isEqualTo(2);

        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
        mockServer.verify();
    }

    @Test
    void indexingIsRejectedForEmptyTranscript() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, UUID.randomUUID(), "Lecture 2", AssetStatus.TRANSCRIPT_READY);
        ProcessingJob processingJob = processingJob(assetId, "task-2", "video-2");

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetService.loadUsableTranscriptSnapshot(asset, processingJob))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Transcript is empty for this asset"));

        assertThatThrownBy(() -> transcriptIndexingService.indexAssetTranscript(assetId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) exception;
                    assertThat(responseStatusException.getStatusCode().value()).isEqualTo(409);
                    assertThat(responseStatusException.getReason()).isEqualTo("Transcript is empty for this asset");
                });

        verify(assetPersistenceService, never()).updateAssetStatus(eq(asset), eq(AssetStatus.SEARCHABLE));
    }

    @Test
    void partialBulkFailureDoesNotMarkAssetAsSearchable() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, UUID.randomUUID(), "Lecture 3", AssetStatus.TRANSCRIPT_READY);
        ProcessingJob processingJob = processingJob(assetId, "task-3", "video-3");
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                transcriptRow(assetId, "row-3", "video-3", 0, "Heap property explanation"),
                transcriptRow(assetId, "row-4", "video-3", 1, "Heap sort walkthrough")
        );

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetService.loadUsableTranscriptSnapshot(asset, processingJob)).thenReturn(transcriptRows);

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("{\"index\":{\"_id\":\"" + assetId + "-row-3\"}}")))
                .andExpect(content().string(containsString("{\"index\":{\"_id\":\"" + assetId + "-row-4\"}}")))
                .andRespond(withSuccess("""
                        {
                          "errors": true,
                          "items": [
                            {"index": {"_id": "%s-row-3", "status": 201}},
                            {"index": {"_id": "%s-row-4", "status": 429, "error": {"reason": "queue full"}}}
                          ]
                        }
                        """.formatted(assetId, assetId), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> transcriptIndexingService.indexAssetTranscript(assetId))
                .isInstanceOf(ElasticsearchIntegrationException.class)
                .hasMessageContaining("Elasticsearch bulk indexing failed for document " + assetId + "-row-4")
                .hasMessageContaining("queue full");

        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.FAILED);
        mockServer.verify();
    }

    @Test
    void indexingFailureDoesNotMarkAssetAsSearchableWhenBulkRequestFails() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, UUID.randomUUID(), "Lecture 4", AssetStatus.TRANSCRIPT_READY);
        ProcessingJob processingJob = processingJob(assetId, "task-4", "video-4");
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                transcriptRow(assetId, "row-5", "video-4", 0, "Red black tree overview")
        );

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetService.loadUsableTranscriptSnapshot(asset, processingJob)).thenReturn(transcriptRows);

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> transcriptIndexingService.indexAssetTranscript(assetId))
                .isInstanceOf(ElasticsearchIntegrationException.class)
                .hasMessageContaining("Elasticsearch returned HTTP 500");

        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.FAILED);
        mockServer.verify();
    }

    @Test
    void repeatedIndexingReusesStableDocumentIdsForSameAsset() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset firstAsset = asset(assetId, workspaceId, "Lecture 5", AssetStatus.TRANSCRIPT_READY);
        Asset searchableAsset = asset(assetId, workspaceId, "Lecture 5", AssetStatus.SEARCHABLE);
        ProcessingJob processingJob = processingJob(assetId, "task-5", "video-5");
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                transcriptRow(assetId, "row-6", "video-5", 0, "Graph traversal overview"),
                transcriptRow(assetId, "row-7", "video-5", 1, "Breadth first search example")
        );

        when(assetService.getAsset(assetId)).thenReturn(firstAsset, searchableAsset);
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetService.loadUsableTranscriptSnapshot(firstAsset, processingJob)).thenReturn(transcriptRows);
        when(assetService.loadUsableTranscriptSnapshot(searchableAsset, processingJob)).thenReturn(transcriptRows);

        mockServer.expect(twice(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("{\"index\":{\"_id\":\"" + assetId + "-row-6\"}}")))
                .andExpect(content().string(containsString("{\"index\":{\"_id\":\"" + assetId + "-row-7\"}}")))
                .andExpect(content().string(containsString("\"workspaceId\":\"" + workspaceId + "\"")))
                .andRespond(withSuccess("""
                        {
                          "errors": false,
                          "items": [
                            {"index": {"_id": "%s-row-6", "status": 200}},
                            {"index": {"_id": "%s-row-7", "status": 200}}
                          ]
                        }
                        """.formatted(assetId, assetId), MediaType.APPLICATION_JSON));

        mockServer.expect(twice(), requestTo("http://localhost:9201/asset-transcript-rows/_refresh"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        AssetIndexResponse firstResponse = transcriptIndexingService.indexAssetTranscript(assetId);
        AssetIndexResponse secondResponse = transcriptIndexingService.indexAssetTranscript(assetId);

        assertThat(firstResponse.indexedDocumentCount()).isEqualTo(2);
        assertThat(secondResponse.indexedDocumentCount()).isEqualTo(2);
        verify(assetPersistenceService).updateAssetStatus(firstAsset, AssetStatus.SEARCHABLE);
        verify(assetPersistenceService).updateAssetStatus(searchableAsset, AssetStatus.SEARCHABLE);
        mockServer.verify();
    }

    @Test
    void partialBulkFailureKeepsAlreadySearchableAssetSearchable() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, UUID.randomUUID(), "Lecture 6", AssetStatus.SEARCHABLE);
        ProcessingJob processingJob = processingJob(assetId, "task-6", "video-6");
        List<AssetTranscriptRowSnapshot> transcriptRows = List.of(
                transcriptRow(assetId, "row-8", "video-6", 0, "Union find recap")
        );

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetService.loadUsableTranscriptSnapshot(asset, processingJob)).thenReturn(transcriptRows);

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_bulk"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "errors": true,
                          "items": [
                            {"index": {"_id": "%s-row-8", "status": 409, "error": {"reason": "version conflict"}}}
                          ]
                        }
                        """.formatted(assetId), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> transcriptIndexingService.indexAssetTranscript(assetId))
                .isInstanceOf(ElasticsearchIntegrationException.class)
                .hasMessageContaining("version conflict");

        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.FAILED);
        mockServer.verify();
    }

    private Asset asset(UUID assetId, UUID workspaceId, String title, AssetStatus status) {
        Asset asset = new Asset("lecture.mp4", title, status, new Workspace(workspaceId, "Study Workspace"));
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }

    private ProcessingJob processingJob(UUID assetId, String taskId, String videoId) {
        return new ProcessingJob(
                assetId,
                taskId,
                videoId,
                ProcessingJobStatus.SUCCEEDED,
                "success"
        );
    }

    private AssetTranscriptRowSnapshot transcriptRow(
            UUID assetId,
            String transcriptRowId,
            String videoId,
            int segmentIndex,
            String text
    ) {
        return new AssetTranscriptRowSnapshot(
                assetId,
                transcriptRowId,
                videoId,
                segmentIndex,
                text,
                "2026-03-26T00:00:00Z"
        );
    }
}
