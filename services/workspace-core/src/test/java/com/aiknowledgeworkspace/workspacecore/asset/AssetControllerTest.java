package com.aiknowledgeworkspace.workspacecore.asset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.search.TranscriptIndexingService;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

        mockMvc = MockMvcBuilders.standaloneSetup(assetController)
                .setControllerAdvice(new ApiExceptionHandler())
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
}
