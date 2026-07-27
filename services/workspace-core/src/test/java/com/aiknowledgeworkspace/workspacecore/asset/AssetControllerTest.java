package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web.AssetController;

import com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web.AssetApiExceptionHandler;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidYouTubeUrlException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.DuplicateYouTubeAssetException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetProcessingRetryNotAllowedException;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetCommandUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetQueryUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetUploadUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.YouTubeAssetCreationUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetPage;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetStatusView;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetSummary;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetView;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetUploadResult;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetProcessingResult;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;
import com.aiknowledgeworkspace.workspacecore.common.web.adapter.in.web.ApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.adapter.in.web.SearchApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.search.api.ExplicitIndexingUseCase;
import com.aiknowledgeworkspace.workspacecore.storage.adapter.in.web.ObjectStorageApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.storage.application.exception.ObjectStorageException;
import com.aiknowledgeworkspace.workspacecore.workspace.adapter.in.web.WorkspaceApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.workspace.application.exception.WorkspaceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssetControllerTest {

    private AssetQueryUseCase assetQueries;
    private AssetUploadUseCase assetUpload;
    private AssetCommandUseCase assetCommands;
    private YouTubeAssetCreationUseCase youtubeAssetCreation;
    private ExplicitIndexingUseCase explicitIndexing;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        assetQueries = mock(AssetQueryUseCase.class);
        assetUpload = mock(AssetUploadUseCase.class);
        assetCommands = mock(AssetCommandUseCase.class);
        youtubeAssetCreation = mock(YouTubeAssetCreationUseCase.class);
        explicitIndexing = mock(ExplicitIndexingUseCase.class);
        AssetController controller = new AssetController(
                assetQueries, assetUpload, assetCommands, youtubeAssetCreation, explicitIndexing
        );
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(
                        new ApiExceptionHandler(),
                        new AssetApiExceptionHandler(),
                        new SearchApiExceptionHandler(),
                        new ObjectStorageApiExceptionHandler(),
                        new WorkspaceApiExceptionHandler()
                )
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void uploadMapsMultipartTransportToApplicationCommand() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecture.mp4", "video/mp4", "video-bytes".getBytes()
        );
        when(assetUpload.upload(any())).thenReturn(new AssetUploadResult(
                assetId, jobId, AssetStatus.PROCESSING, workspaceId, AssetSourceType.UPLOAD, null
        ));

        mockMvc.perform(multipart("/api/assets/upload")
                        .file(file)
                        .param("workspaceId", workspaceId.toString())
                        .param("title", "Lecture 1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.processingJobId").value(jobId.toString()))
                .andExpect(jsonPath("$.assetStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.sourceType").value("UPLOAD"))
                .andExpect(jsonPath("$.youtubeVideoId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sourceUrl").value(org.hamcrest.Matchers.nullValue()));

        verify(assetUpload).upload(argThat(command ->
                workspaceId.equals(command.workspaceId())
                        && "lecture.mp4".equals(command.originalFilename())
                        && "video/mp4".equals(command.contentType())
                        && command.sizeBytes() == 11L
                        && "Lecture 1".equals(command.requestedTitle())
                        && command.content() != null
        ));
    }

    @Test
    void transcriptEndpointExposesNullableMillisecondTimingAdditively() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetQueries.getAssetTranscript(assetId)).thenReturn(List.of(
                new AssetTranscriptRowView(
                        "row-1", "video-1", 0, 0L, 1250L,
                        "Timed transcript", "2026-07-22T00:00:00Z"
                ),
                new AssetTranscriptRowView(
                        "row-2", "video-1", 1, null, null,
                        "Legacy transcript", "2026-07-22T00:00:01Z"
                )
        ));

        mockMvc.perform(get("/api/assets/{assetId}/transcript", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startMs").value(0))
                .andExpect(jsonPath("$[0].endMs").value(1250))
                .andExpect(jsonPath("$[1].startMs").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].endMs").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void uploadKeepsStructuredStorageFailureWithoutLeakingDetails() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lecture.mp4", "video/mp4", new byte[]{1});
        when(assetUpload.upload(any())).thenThrow(
                new ObjectStorageException("Object storage upload failed", new RuntimeException("minio secret"))
        );

        mockMvc.perform(multipart("/api/assets/upload").file(file))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_SERVICE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("minio secret"))));
    }

    @Test
    void listMapsApplicationPageToStableHttpShape() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(assetQueries.listAssets(workspaceId, null, null, null)).thenReturn(new AssetPage(
                List.of(new AssetSummary(
                        assetId,
                        "Lecture 1",
                        AssetStatus.SEARCHABLE,
                        workspaceId,
                        AssetSourceType.UPLOAD,
                        null,
                        Instant.parse("2026-04-10T03:00:00Z")
                )),
                0,
                20,
                1,
                1,
                false
        ));

        mockMvc.perform(get("/api/assets").param("workspaceId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.items[0].assetStatus").value("SEARCHABLE"))
                .andExpect(jsonPath("$.items[0].sourceType").value("UPLOAD"))
                .andExpect(jsonPath("$.items[0].youtubeVideoId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].sourceUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void getReturnsApplicationViewRatherThanJpaEntity() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(assetQueries.getAsset(assetId)).thenReturn(view(assetId, workspaceId, "Lecture 1"));

        mockMvc.perform(get("/api/assets/{assetId}", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assetId.toString()))
                .andExpect(jsonPath("$.title").value("Lecture 1"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.sourceType").value("UPLOAD"))
                .andExpect(jsonPath("$.youtubeVideoId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.originalFilename").value("lecture.mp4"))
                .andExpect(jsonPath("$.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.sizeBytes").value(42));
    }

    @Test
    void getExposesYoutubeIdentityWhileUploadFieldsRemainNullable() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(assetQueries.getAsset(assetId)).thenReturn(new AssetView(
                assetId,
                null,
                "YouTube lecture",
                AssetStatus.PROCESSING,
                workspaceId,
                AssetSourceType.YOUTUBE,
                "video-id",
                null,
                null,
                Instant.parse("2026-04-10T03:00:00Z"),
                Instant.parse("2026-04-10T03:05:00Z")
        ));

        mockMvc.perform(get("/api/assets/{assetId}", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("YOUTUBE"))
                .andExpect(jsonPath("$.youtubeVideoId").value("video-id"))
                .andExpect(jsonPath("$.sourceUrl").value("https://www.youtube.com/watch?v=video-id"))
                .andExpect(jsonPath("$.originalFilename").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.contentType").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sizeBytes").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void statusMapsApplicationResult() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(assetQueries.getAssetStatus(assetId)).thenReturn(new AssetStatusView(
                assetId, jobId, AssetStatus.TRANSCRIPT_READY, ProcessingJobStatus.SUCCEEDED, null
        ));

        mockMvc.perform(get("/api/assets/{assetId}/status", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.processingJobStatus").value("SUCCEEDED"));
    }

    @Test
    void statusExposesOnlyTheSanitizedFailureCode() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(assetQueries.getAssetStatus(assetId)).thenReturn(new AssetStatusView(
                assetId,
                jobId,
                AssetStatus.FAILED,
                ProcessingJobStatus.FAILED,
                "YOUTUBE_ACQUISITION_TIMEOUT"
        ));

        mockMvc.perform(get("/api/assets/{assetId}/status", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureCode").value("YOUTUBE_ACQUISITION_TIMEOUT"))
                .andExpect(content().string(not(containsString("stderr"))));
    }

    @Test
    void youtubeCreateMapsJsonToTheApplicationBoundaryAndReturnsDerivedSourceUrl() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(youtubeAssetCreation.create(any())).thenReturn(new AssetProcessingResult(
                assetId,
                jobId,
                AssetStatus.PROCESSING,
                workspaceId,
                AssetSourceType.YOUTUBE,
                "abc_DEF-123"
        ));

        mockMvc.perform(post("/api/assets/youtube")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": "%s",
                                  "url": "https://youtu.be/abc_DEF-123?t=42",
                                  "title": "Lecture"
                                }
                                """.formatted(workspaceId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.processingJobId").value(jobId.toString()))
                .andExpect(jsonPath("$.assetStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.sourceType").value("YOUTUBE"))
                .andExpect(jsonPath("$.youtubeVideoId").value("abc_DEF-123"))
                .andExpect(jsonPath("$.sourceUrl")
                        .value("https://www.youtube.com/watch?v=abc_DEF-123"));

        verify(youtubeAssetCreation).create(argThat(command ->
                workspaceId.equals(command.workspaceId())
                        && "https://youtu.be/abc_DEF-123?t=42".equals(command.url())
                        && "Lecture".equals(command.requestedTitle())
        ));
    }

    @Test
    void youtubeCreateMapsInvalidAndDuplicateErrorsToStableCodes() throws Exception {
        when(youtubeAssetCreation.create(any()))
                .thenThrow(new InvalidYouTubeUrlException("A supported public YouTube HTTPS URL is required"))
                .thenThrow(new DuplicateYouTubeAssetException());

        mockMvc.perform(post("/api/assets/youtube")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.invalid/video\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_YOUTUBE_URL"));

        mockMvc.perform(post("/api/assets/youtube")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://youtu.be/abc_DEF-123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_YOUTUBE_ASSET"))
                .andExpect(content().string(not(containsString("uk_assets_workspace_youtube_video"))));
    }

    @Test
    void retryReturnsAcceptedWithSourceSpecificMetadata() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(assetCommands.retryProcessing(assetId)).thenReturn(new AssetProcessingResult(
                assetId,
                jobId,
                AssetStatus.PROCESSING,
                workspaceId,
                AssetSourceType.YOUTUBE,
                "abc_DEF-123"
        ));

        mockMvc.perform(post("/api/assets/{assetId}/retry-processing", assetId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processingJobId").value(jobId.toString()))
                .andExpect(jsonPath("$.assetStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.sourceType").value("YOUTUBE"))
                .andExpect(jsonPath("$.sourceUrl")
                        .value("https://www.youtube.com/watch?v=abc_DEF-123"));
    }

    @Test
    void retryConflictUsesTheStablePublicErrorCode() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetCommands.retryProcessing(assetId))
                .thenThrow(new AssetProcessingRetryNotAllowedException());

        mockMvc.perform(post("/api/assets/{assetId}/retry-processing", assetId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSET_PROCESSING_RETRY_NOT_ALLOWED"));
    }

    @Test
    void unauthorizedRetryUsesTheExistingNotFoundSemantics() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetCommands.retryProcessing(assetId)).thenThrow(new AssetNotFoundException());

        mockMvc.perform(post("/api/assets/{assetId}/retry-processing", assetId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));
    }

    @Test
    void updatePassesTransportValueToCommandBoundary() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(assetCommands.updateTitle(assetId, "New Title"))
                .thenReturn(view(assetId, workspaceId, "New Title"));

        mockMvc.perform(patch("/api/assets/{assetId}", assetId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"));
    }

    @Test
    void deleteUsesCommandBoundary() throws Exception {
        UUID assetId = UUID.randomUUID();

        mockMvc.perform(delete("/api/assets/{assetId}", assetId))
                .andExpect(status().isNoContent());

        verify(assetCommands).delete(assetId);
    }

    @Test
    void nonOwnedWorkspaceRemainsNotFound() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(assetQueries.listAssets(workspaceId, null, null, null))
                .thenThrow(new WorkspaceNotFoundException(workspaceId));

        mockMvc.perform(get("/api/assets").param("workspaceId", workspaceId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    void nonOwnedAssetRemainsNotFound() throws Exception {
        UUID assetId = UUID.randomUUID();
        doThrow(new AssetNotFoundException()).when(assetCommands).delete(assetId);

        mockMvc.perform(delete("/api/assets/{assetId}", assetId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));
    }

    private AssetView view(UUID assetId, UUID workspaceId, String title) {
        return new AssetView(
                assetId,
                "lecture.mp4",
                title,
                AssetStatus.SEARCHABLE,
                workspaceId,
                AssetSourceType.UPLOAD,
                null,
                "video/mp4",
                42L,
                Instant.parse("2026-04-10T03:00:00Z"),
                Instant.parse("2026-04-10T03:05:00Z")
        );
    }
}
