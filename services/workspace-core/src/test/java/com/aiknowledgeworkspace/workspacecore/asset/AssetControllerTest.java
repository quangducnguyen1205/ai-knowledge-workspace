package com.aiknowledgeworkspace.workspacecore.asset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.aiknowledgeworkspace.workspacecore.search.TranscriptIndexingService;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceNotFoundException;
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

    private AssetService assetService;
    private AssetDeletionService assetDeletionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        assetService = mock(AssetService.class);
        assetDeletionService = mock(AssetDeletionService.class);
        TranscriptIndexingService transcriptIndexingService = mock(TranscriptIndexingService.class);
        AssetController assetController = new AssetController(assetService, assetDeletionService, transcriptIndexingService);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(assetController)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void uploadReturnsStructuredNotFoundForUnknownWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.mp4",
                "video/mp4",
                "video-bytes".getBytes()
        );
        when(assetService.uploadAsset(workspaceId, file, "Lecture 1"))
                .thenThrow(new WorkspaceNotFoundException(workspaceId));

        mockMvc.perform(multipart("/api/assets/upload")
                        .file(file)
                        .param("workspaceId", workspaceId.toString())
                        .param("title", "Lecture 1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Workspace not found: " + workspaceId));
    }

    @Test
    void listAssetsReturnsWorkspaceScopedAssetSummaries() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(assetService.listAssets(workspaceId)).thenReturn(List.of(new AssetSummaryResponse(
                assetId,
                "Lecture 1",
                AssetStatus.SEARCHABLE,
                workspaceId,
                Instant.parse("2026-04-10T03:00:00Z")
        )));

        mockMvc.perform(get("/api/assets")
                        .param("workspaceId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assetId").value(assetId.toString()))
                .andExpect(jsonPath("$[0].title").value("Lecture 1"))
                .andExpect(jsonPath("$[0].assetStatus").value("SEARCHABLE"))
                .andExpect(jsonPath("$[0].workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$[0].createdAt").value("2026-04-10T03:00:00Z"));
    }

    @Test
    void listAssetsReturnsStructuredBadRequestForMalformedWorkspaceId() throws Exception {
        mockMvc.perform(get("/api/assets")
                        .param("workspaceId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"))
                .andExpect(jsonPath("$.message").value("workspaceId must be a valid UUID"));
    }

    @Test
    void listAssetsReturnsStructuredNotFoundForUnknownWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(assetService.listAssets(workspaceId))
                .thenThrow(new WorkspaceNotFoundException(workspaceId));

        mockMvc.perform(get("/api/assets")
                        .param("workspaceId", workspaceId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Workspace not found: " + workspaceId));
    }

    @Test
    void deleteAssetReturnsNoContent() throws Exception {
        UUID assetId = UUID.randomUUID();

        mockMvc.perform(delete("/api/assets/{assetId}", assetId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAssetReturnsNotFoundWhenAssetDoesNotExist() throws Exception {
        UUID assetId = UUID.randomUUID();
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Asset not found"
        )).when(assetDeletionService).deleteAsset(assetId);

        mockMvc.perform(delete("/api/assets/{assetId}", assetId))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Asset not found"));
    }

    @Test
    void transcriptContextReturnsTranscriptWindow() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAssetTranscriptContext(assetId, "row-2", 2))
                .thenReturn(new AssetTranscriptContextResponse(
                        assetId,
                        "row-2",
                        2,
                        2,
                        List.of(
                                new AssetTranscriptRowResponse(
                                        "row-1",
                                        "video-1",
                                        1,
                                        "Context before",
                                        "2026-04-11T00:00:00Z"
                                ),
                                new AssetTranscriptRowResponse(
                                        "row-2",
                                        "video-1",
                                        2,
                                        "Matched row",
                                        "2026-04-11T00:00:01Z"
                                )
                        )
                ));

        mockMvc.perform(get("/api/assets/{assetId}/transcript/context", assetId)
                        .param("transcriptRowId", "row-2")
                        .param("window", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.transcriptRowId").value("row-2"))
                .andExpect(jsonPath("$.hitSegmentIndex").value(2))
                .andExpect(jsonPath("$.window").value(2))
                .andExpect(jsonPath("$.rows[0].id").value("row-1"))
                .andExpect(jsonPath("$.rows[1].id").value("row-2"));
    }

    @Test
    void transcriptContextReturnsStructuredBadRequestForInvalidWindow() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAssetTranscriptContext(assetId, "row-2", 0))
                .thenThrow(new InvalidTranscriptContextWindowException("window must be greater than 0"));

        mockMvc.perform(get("/api/assets/{assetId}/transcript/context", assetId)
                        .param("transcriptRowId", "row-2")
                        .param("window", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSCRIPT_CONTEXT_WINDOW"))
                .andExpect(jsonPath("$.message").value("window must be greater than 0"));
    }

    @Test
    void transcriptContextReturnsStructuredNotFoundForMissingTranscriptRow() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAssetTranscriptContext(assetId, "row-404", 2))
                .thenThrow(new TranscriptRowNotFoundException(assetId, "row-404"));

        mockMvc.perform(get("/api/assets/{assetId}/transcript/context", assetId)
                        .param("transcriptRowId", "row-404")
                        .param("window", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSCRIPT_ROW_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Transcript row not found for asset " + assetId + ": row-404"));
    }

    @Test
    void transcriptContextReturnsStructuredBadRequestForMalformedWindowType() throws Exception {
        UUID assetId = UUID.randomUUID();

        mockMvc.perform(get("/api/assets/{assetId}/transcript/context", assetId)
                        .param("transcriptRowId", "row-2")
                        .param("window", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSCRIPT_CONTEXT_WINDOW"))
                .andExpect(jsonPath("$.message").value("window must be a valid integer"));
    }
}
