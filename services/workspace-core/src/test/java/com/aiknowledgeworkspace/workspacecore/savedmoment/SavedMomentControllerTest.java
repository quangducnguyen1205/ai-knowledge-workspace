package com.aiknowledgeworkspace.workspacecore.savedmoment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.common.web.adapter.in.web.ApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.savedmoment.adapter.in.web.SavedMomentApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.savedmoment.adapter.in.web.SavedMomentController;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.command.SaveMomentCommand;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.InvalidSavedMomentRequestException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.SavedMomentNotFoundException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.SavedMomentTargetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.in.SavedMomentUseCase;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.result.SavedMomentListView;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.result.SavedMomentView;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SavedMomentControllerTest {

    private static final UUID SAVED_MOMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant SAVED_AT = Instant.parse("2026-07-30T08:00:00Z");

    private SavedMomentUseCase savedMoments;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        savedMoments = mock(SavedMomentUseCase.class);
        // Matches the Spring Boot auto-configured mapper: unknown client fields are ignored.
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mockMvc = MockMvcBuilders.standaloneSetup(new SavedMomentController(savedMoments))
                .setControllerAdvice(new ApiExceptionHandler(), new SavedMomentApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void savingReturnsTheCanonicalRepresentationNeededToReopenTheMoment() throws Exception {
        when(savedMoments.save(any())).thenReturn(view("row-1", 7, 1000L, 4000L));

        mockMvc.perform(post("/api/saved-moments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"" + ASSET_ID + "\",\"transcriptRowId\":\"row-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedMomentId").value(SAVED_MOMENT_ID.toString()))
                .andExpect(jsonPath("$.workspaceId").value(WORKSPACE_ID.toString()))
                .andExpect(jsonPath("$.assetId").value(ASSET_ID.toString()))
                .andExpect(jsonPath("$.assetTitle").value("Lecture"))
                .andExpect(jsonPath("$.sourceType").value("UPLOAD"))
                .andExpect(jsonPath("$.transcriptRowId").value("row-1"))
                .andExpect(jsonPath("$.segmentIndex").value(7))
                .andExpect(jsonPath("$.startMs").value(1000))
                .andExpect(jsonPath("$.endMs").value(4000))
                .andExpect(jsonPath("$.text").value("Canonical text."))
                .andExpect(jsonPath("$.savedAt").value("2026-07-30T08:00:00Z"));
    }

    @Test
    void aClientSuppliedWorkspaceIdIsIgnoredBecauseOwnershipResolvesIt() throws Exception {
        when(savedMoments.save(any())).thenReturn(view("row-1", 7, 1000L, 4000L));

        mockMvc.perform(post("/api/saved-moments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"" + ASSET_ID + "\",\"transcriptRowId\":\"row-1\","
                                + "\"workspaceId\":\"" + UUID.randomUUID() + "\",\"score\":9.5}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SaveMomentCommand> captor = ArgumentCaptor.forClass(SaveMomentCommand.class);
        verify(savedMoments).save(captor.capture());
        assertThat(captor.getValue().assetId()).isEqualTo(ASSET_ID);
        assertThat(captor.getValue().transcriptRowId()).isEqualTo("row-1");
    }

    @Test
    void nullableTimingIsRenderedAsJsonNullRatherThanZero() throws Exception {
        when(savedMoments.save(any())).thenReturn(view("row-1", 7, null, null));

        mockMvc.perform(post("/api/saved-moments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"" + ASSET_ID + "\",\"transcriptRowId\":\"row-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startMs").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.endMs").value(Matchers.nullValue()));
    }

    @Test
    void listReturnsTheWorkspaceScopedBoundedCollection() throws Exception {
        when(savedMoments.listForWorkspace(WORKSPACE_ID)).thenReturn(new SavedMomentListView(
                WORKSPACE_ID, 2, 100, List.of(view("row-2", 8, 2000L, 3000L), view("row-1", 7, 1000L, 4000L))
        ));

        mockMvc.perform(get("/api/saved-moments").param("workspaceId", WORKSPACE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceIdFilter").value(WORKSPACE_ID.toString()))
                .andExpect(jsonPath("$.savedMomentCount").value(2))
                .andExpect(jsonPath("$.maxItems").value(100))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].transcriptRowId").value("row-2"))
                .andExpect(jsonPath("$.items[1].transcriptRowId").value("row-1"));
    }

    @Test
    void listWithoutAWorkspaceParameterDelegatesTheDefaultResolution() throws Exception {
        when(savedMoments.listForWorkspace(null))
                .thenReturn(new SavedMomentListView(WORKSPACE_ID, 0, 100, List.of()));

        mockMvc.perform(get("/api/saved-moments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceIdFilter").value(WORKSPACE_ID.toString()))
                .andExpect(jsonPath("$.items.length()").value(0));

        verify(savedMoments).listForWorkspace(null);
    }

    @Test
    void removeReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/saved-moments/{savedMomentId}", SAVED_MOMENT_ID))
                .andExpect(status().isNoContent());

        verify(savedMoments).remove(SAVED_MOMENT_ID);
    }

    @Test
    void aForeignOrUnknownSavedMomentIsASafeNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new SavedMomentNotFoundException())
                .when(savedMoments).remove(SAVED_MOMENT_ID);

        mockMvc.perform(delete("/api/saved-moments/{savedMomentId}", SAVED_MOMENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SAVED_MOMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Saved moment not found"))
                .andExpect(jsonPath("$.assetId").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void aForeignAssetOrRemovedRowIsASafeNotFoundWithoutRevealingWhichOne() throws Exception {
        when(savedMoments.save(any())).thenThrow(new SavedMomentTargetNotFoundException());

        mockMvc.perform(post("/api/saved-moments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"" + ASSET_ID + "\",\"transcriptRowId\":\"row-1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SAVED_MOMENT_TARGET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Video moment not found"))
                .andExpect(jsonPath("$.transcriptRowId").doesNotExist());
    }

    @Test
    void invalidRequestsAreRejectedWithASafeClientError() throws Exception {
        when(savedMoments.save(any()))
                .thenThrow(new InvalidSavedMomentRequestException("transcriptRowId is required"));

        mockMvc.perform(post("/api/saved-moments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"" + ASSET_ID + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SAVED_MOMENT_REQUEST"))
                .andExpect(jsonPath("$.message").value("transcriptRowId is required"));
    }

    @Test
    void aMalformedBodyIsRejectedBeforeReachingTheUseCase() throws Exception {
        mockMvc.perform(post("/api/saved-moments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(savedMoments);
    }

    @Test
    void errorBodiesNeverCarrySqlTableNamesOrStackTraces() throws Exception {
        when(savedMoments.save(any())).thenThrow(new SavedMomentTargetNotFoundException());

        String body = mockMvc.perform(post("/api/saved-moments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"" + ASSET_ID + "\",\"transcriptRowId\":\"row-1\"}"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("saved_moments", "select", "SELECT", "Exception", "org.springframework");
    }

    private SavedMomentView view(String transcriptRowId, Integer segmentIndex, Long startMs, Long endMs) {
        return new SavedMomentView(
                SAVED_MOMENT_ID,
                WORKSPACE_ID,
                ASSET_ID,
                "Lecture",
                "UPLOAD",
                transcriptRowId,
                segmentIndex,
                startMs,
                endMs,
                "Canonical text.",
                SAVED_AT
        );
    }
}
