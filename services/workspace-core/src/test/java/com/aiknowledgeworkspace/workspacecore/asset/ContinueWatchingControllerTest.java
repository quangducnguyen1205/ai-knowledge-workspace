package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web.ContinueWatchingController;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.ContinueWatchingUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.ContinueWatchingItem;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.ContinueWatchingListView;
import com.aiknowledgeworkspace.workspacecore.common.web.adapter.in.web.ApiExceptionHandler;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ContinueWatchingControllerTest {

    private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-30T08:00:00Z");

    private ContinueWatchingUseCase continueWatching;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        continueWatching = mock(ContinueWatchingUseCase.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new ContinueWatchingController(continueWatching))
                .setControllerAdvice(new ApiExceptionHandler(), new WorkspaceApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void theListCarriesEveryFieldNeededToPresentAndReopenTheAsset() throws Exception {
        when(continueWatching.listForWorkspace(WORKSPACE_ID)).thenReturn(new ContinueWatchingListView(
                WORKSPACE_ID, 1, 12, List.of(item("Vector Clocks Lecture", "UPLOAD", 61_000))
        ));

        mockMvc.perform(get("/api/playback-progress").param("workspaceId", WORKSPACE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceIdFilter").value(WORKSPACE_ID.toString()))
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.maxItems").value(12))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].assetId").value(ASSET_ID.toString()))
                .andExpect(jsonPath("$.items[0].workspaceId").value(WORKSPACE_ID.toString()))
                .andExpect(jsonPath("$.items[0].assetTitle").value("Vector Clocks Lecture"))
                .andExpect(jsonPath("$.items[0].sourceType").value("UPLOAD"))
                .andExpect(jsonPath("$.items[0].positionMs").value(61_000))
                .andExpect(jsonPath("$.items[0].completed").value(false))
                .andExpect(jsonPath("$.items[0].updatedAt").value("2026-07-30T08:00:00Z"));
    }

    @Test
    void anOmittedWorkspaceDelegatesDefaultResolutionToTheUseCase() throws Exception {
        when(continueWatching.listForWorkspace(null))
                .thenReturn(new ContinueWatchingListView(WORKSPACE_ID, 0, 12, List.of()));

        mockMvc.perform(get("/api/playback-progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceIdFilter").value(WORKSPACE_ID.toString()))
                .andExpect(jsonPath("$.items.length()").value(0));

        verify(continueWatching).listForWorkspace(null);
    }

    @Test
    void anEmptyListIsASuccessfulResponseRatherThanAnError() throws Exception {
        when(continueWatching.listForWorkspace(WORKSPACE_ID))
                .thenReturn(new ContinueWatchingListView(WORKSPACE_ID, 0, 12, List.of()));

        mockMvc.perform(get("/api/playback-progress").param("workspaceId", WORKSPACE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void aForeignWorkspaceIsASafeNotFound() throws Exception {
        when(continueWatching.listForWorkspace(WORKSPACE_ID))
                .thenThrow(new WorkspaceNotFoundException(WORKSPACE_ID));

        String body = mockMvc.perform(get("/api/playback-progress")
                        .param("workspaceId", WORKSPACE_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("asset_playback_progress", "select", "Exception", "org.springframework");
    }

    @Test
    void aMalformedWorkspaceIdentifierUsesTheSameCodeAsEveryOtherWorkspaceScopedEndpoint() throws Exception {
        mockMvc.perform(get("/api/playback-progress").param("workspaceId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"));

        org.mockito.Mockito.verifyNoInteractions(continueWatching);
    }

    @Test
    void thereIsNoPaginationOrClientControlledLimit() throws Exception {
        when(continueWatching.listForWorkspace(WORKSPACE_ID)).thenReturn(new ContinueWatchingListView(
                WORKSPACE_ID, 1, 12, List.of(item("Lecture", "YOUTUBE", 1_000))
        ));

        mockMvc.perform(get("/api/playback-progress")
                        .param("workspaceId", WORKSPACE_ID.toString())
                        .param("page", "3")
                        .param("size", "50")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxItems").value(12))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").doesNotExist())
                .andExpect(jsonPath("$.totalPages").doesNotExist());

        verify(continueWatching).listForWorkspace(WORKSPACE_ID);
    }

    private ContinueWatchingItem item(String title, String sourceType, long positionMs) {
        return new ContinueWatchingItem(ASSET_ID, WORKSPACE_ID, title, sourceType, positionMs, false, UPDATED_AT);
    }
}
