package com.aiknowledgeworkspace.workspacecore.asset;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetCommandUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetQueryUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetUploadUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetPage;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetStatusView;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetSummary;
import com.aiknowledgeworkspace.workspacecore.asset.application.query.AssetView;
import com.aiknowledgeworkspace.workspacecore.asset.application.upload.AssetUploadResult;
import com.aiknowledgeworkspace.workspacecore.common.web.ApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.processing.application.ProcessingJobStatus;
import com.aiknowledgeworkspace.workspacecore.search.SearchApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.search.application.ExplicitIndexingApplication;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.storage.ObjectStorageException;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceNotFoundException;
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
    private ExplicitIndexingApplication explicitIndexing;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        assetQueries = mock(AssetQueryUseCase.class);
        assetUpload = mock(AssetUploadUseCase.class);
        assetCommands = mock(AssetCommandUseCase.class);
        explicitIndexing = mock(ExplicitIndexingApplication.class);
        AssetController controller = new AssetController(assetQueries, assetUpload, assetCommands, explicitIndexing);
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
                assetId, jobId, AssetStatus.PROCESSING, workspaceId
        ));

        mockMvc.perform(multipart("/api/assets/upload")
                        .file(file)
                        .param("workspaceId", workspaceId.toString())
                        .param("title", "Lecture 1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.processingJobId").value(jobId.toString()))
                .andExpect(jsonPath("$.assetStatus").value("PROCESSING"));

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
                .andExpect(jsonPath("$.items[0].assetStatus").value("SEARCHABLE"));
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
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()));
    }

    @Test
    void statusMapsApplicationResult() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(assetQueries.getAssetStatus(assetId)).thenReturn(new AssetStatusView(
                assetId, jobId, AssetStatus.TRANSCRIPT_READY, ProcessingJobStatus.SUCCEEDED
        ));

        mockMvc.perform(get("/api/assets/{assetId}/status", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.processingJobStatus").value("SUCCEEDED"));
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
                "video/mp4",
                42L,
                Instant.parse("2026-04-10T03:00:00Z"),
                Instant.parse("2026-04-10T03:05:00Z")
        );
    }
}
