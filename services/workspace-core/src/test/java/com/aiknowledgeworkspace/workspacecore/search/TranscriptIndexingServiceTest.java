package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
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
import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.integration.fastapi.FastApiTranscriptRowResponse;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJob;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobRepository;
import com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
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
                new TranscriptIndexDocumentMapper()
        );
    }

    @Test
    void indexingSucceedsForTranscriptUsableAsset() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = asset(assetId, workspaceId, "Lecture 1", AssetStatus.TRANSCRIPT_READY);
        ProcessingJob processingJob = processingJob(assetId, "task-1", "video-1");
        List<FastApiTranscriptRowResponse> transcriptRows = List.of(
                transcriptRow("row-1", "video-1", 0, "Binary search tree overview"),
                transcriptRow("row-2", "video-1", 1, "Traversal example")
        );

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetService.loadUsableTranscriptRows(asset, processingJob)).thenReturn(transcriptRows);

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_doc/" + assetId + "-row-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(containsString("\"assetTitle\":\"Lecture 1\"")))
                .andExpect(content().string(containsString("\"transcriptRowId\":\"row-1\"")))
                .andExpect(content().string(containsString("\"workspaceId\":\"" + workspaceId + "\"")))
                .andExpect(content().string(containsString("\"assetStatus\":\"SEARCHABLE\"")))
                .andRespond(withSuccess());

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_doc/" + assetId + "-row-2"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(containsString("\"transcriptRowId\":\"row-2\"")))
                .andRespond(withSuccess());

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
        when(assetService.loadUsableTranscriptRows(asset, processingJob))
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
    void indexingFailureDoesNotMarkAssetAsSearchable() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, UUID.randomUUID(), "Lecture 3", AssetStatus.TRANSCRIPT_READY);
        ProcessingJob processingJob = processingJob(assetId, "task-3", "video-3");
        List<FastApiTranscriptRowResponse> transcriptRows = List.of(
                transcriptRow("row-3", "video-3", 0, "Heap property explanation")
        );

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(processingJobRepository.findByAssetId(assetId)).thenReturn(Optional.of(processingJob));
        when(assetService.loadUsableTranscriptRows(asset, processingJob)).thenReturn(transcriptRows);

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_doc/" + assetId + "-row-3"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withServerError());

        assertThatThrownBy(() -> transcriptIndexingService.indexAssetTranscript(assetId))
                .isInstanceOf(ElasticsearchIntegrationException.class)
                .hasMessageContaining("Elasticsearch returned HTTP 500");

        verify(assetPersistenceService).updateAssetStatus(asset, AssetStatus.TRANSCRIPT_READY);
        verify(assetPersistenceService, never()).updateAssetStatus(asset, AssetStatus.SEARCHABLE);
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

    private FastApiTranscriptRowResponse transcriptRow(String id, String videoId, int segmentIndex, String text) {
        return new FastApiTranscriptRowResponse(
                id,
                videoId,
                segmentIndex,
                text,
                "2026-03-26T00:00:00Z"
        );
    }
}
