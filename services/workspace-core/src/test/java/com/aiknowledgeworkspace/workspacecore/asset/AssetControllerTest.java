package com.aiknowledgeworkspace.workspacecore.asset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        assetService = mock(AssetService.class);
        TranscriptIndexingService transcriptIndexingService = mock(TranscriptIndexingService.class);
        AssetController assetController = new AssetController(assetService, transcriptIndexingService);
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
}
