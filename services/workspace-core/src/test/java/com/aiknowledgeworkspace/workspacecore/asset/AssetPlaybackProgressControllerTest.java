package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web.AssetApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web.AssetPlaybackProgressController;
import com.aiknowledgeworkspace.workspacecore.asset.application.command.SaveAssetPlaybackProgressCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidPlaybackProgressException;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.in.AssetPlaybackProgressUseCase;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetPlaybackProgressView;
import com.aiknowledgeworkspace.workspacecore.common.web.adapter.in.web.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssetPlaybackProgressControllerTest {

    private static final Instant SAVED_AT = Instant.parse("2026-07-29T08:00:00Z");

    private AssetPlaybackProgressUseCase playbackProgress;
    private MockMvc mockMvc;
    private UUID assetId;

    @BeforeEach
    void setUp() {
        playbackProgress = mock(AssetPlaybackProgressUseCase.class);
        assetId = UUID.randomUUID();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new AssetPlaybackProgressController(playbackProgress))
                .setControllerAdvice(new ApiExceptionHandler(), new AssetApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void absentProgressIsRenderedAsTheFrozenDefaultRepresentation() throws Exception {
        when(playbackProgress.getProgress(assetId)).thenReturn(AssetPlaybackProgressView.unstarted(assetId));

        mockMvc.perform(get("/api/assets/{assetId}/playback-progress", assetId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.positionMs").value(0))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void storedProgressIsRenderedWithAnIsoInstantTimestamp() throws Exception {
        when(playbackProgress.getProgress(assetId))
                .thenReturn(new AssetPlaybackProgressView(assetId, 12345L, false, SAVED_AT));

        mockMvc.perform(get("/api/assets/{assetId}/playback-progress", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionMs").value(12345))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-29T08:00:00Z"));
    }

    @Test
    void completedProgressKeepsItsLastPositionInTheResponse() throws Exception {
        when(playbackProgress.getProgress(assetId))
                .thenReturn(new AssetPlaybackProgressView(assetId, 53480L, true, SAVED_AT));

        mockMvc.perform(get("/api/assets/{assetId}/playback-progress", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionMs").value(53480))
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void saveMapsTheJsonBodyToTheApplicationCommandAndReturnsTheSameRepresentation() throws Exception {
        when(playbackProgress.saveProgress(eq(assetId), any()))
                .thenReturn(new AssetPlaybackProgressView(assetId, 12345L, false, SAVED_AT));

        mockMvc.perform(put("/api/assets/{assetId}/playback-progress", assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionMs\":12345,\"completed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.positionMs").value(12345))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-29T08:00:00Z"));

        ArgumentCaptor<SaveAssetPlaybackProgressCommand> command =
                ArgumentCaptor.forClass(SaveAssetPlaybackProgressCommand.class);
        org.mockito.Mockito.verify(playbackProgress).saveProgress(eq(assetId), command.capture());
        assertThat(command.getValue().positionMs()).isEqualByComparingTo(BigDecimal.valueOf(12345));
        assertThat(command.getValue().completed()).isFalse();
    }

    @Test
    void neitherReadNorWriteExposesAnOwningUserIdentifier() throws Exception {
        when(playbackProgress.getProgress(assetId))
                .thenReturn(new AssetPlaybackProgressView(assetId, 900L, true, SAVED_AT));

        String body = mockMvc.perform(get("/api/assets/{assetId}/playback-progress", assetId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("userId").doesNotContain("ownerId");
    }

    @Test
    void invalidPositionUsesTheExistingValidationErrorConvention() throws Exception {
        when(playbackProgress.saveProgress(eq(assetId), any()))
                .thenThrow(new InvalidPlaybackProgressException("positionMs must be greater than or equal to 0"));

        mockMvc.perform(put("/api/assets/{assetId}/playback-progress", assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionMs\":-1,\"completed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAYBACK_PROGRESS"))
                .andExpect(jsonPath("$.message").value("positionMs must be greater than or equal to 0"));
    }

    @Test
    void malformedBodyUsesTheExistingRequestBodyErrorConvention() throws Exception {
        mockMvc.perform(put("/api/assets/{assetId}/playback-progress", assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionMs\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(playbackProgress);
    }

    @Test
    void missingOrNullCompletedIsRejectedWithTheValidationErrorConvention() throws Exception {
        when(playbackProgress.saveProgress(eq(assetId), any()))
                .thenThrow(new InvalidPlaybackProgressException("completed is required"));

        for (String body : new String[]{"{\"positionMs\":12345}", "{\"positionMs\":12345,\"completed\":null}"}) {
            mockMvc.perform(put("/api/assets/{assetId}/playback-progress", assetId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PLAYBACK_PROGRESS"))
                    .andExpect(jsonPath("$.message").value("completed is required"));
        }

        ArgumentCaptor<SaveAssetPlaybackProgressCommand> command =
                ArgumentCaptor.forClass(SaveAssetPlaybackProgressCommand.class);
        org.mockito.Mockito.verify(playbackProgress, org.mockito.Mockito.times(2))
                .saveProgress(eq(assetId), command.capture());
        assertThat(command.getAllValues()).allSatisfy(value -> assertThat(value.completed()).isNull());
    }

    @Test
    void bothCompletionValuesAreBoundAndAccepted() throws Exception {
        when(playbackProgress.saveProgress(eq(assetId), any()))
                .thenReturn(new AssetPlaybackProgressView(assetId, 12345L, true, SAVED_AT));

        mockMvc.perform(put("/api/assets/{assetId}/playback-progress", assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionMs\":12345,\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));

        mockMvc.perform(put("/api/assets/{assetId}/playback-progress", assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionMs\":12345,\"completed\":false}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SaveAssetPlaybackProgressCommand> command =
                ArgumentCaptor.forClass(SaveAssetPlaybackProgressCommand.class);
        org.mockito.Mockito.verify(playbackProgress, org.mockito.Mockito.times(2))
                .saveProgress(eq(assetId), command.capture());
        assertThat(command.getAllValues()).extracting(SaveAssetPlaybackProgressCommand::completed)
                .containsExactly(true, false);
    }

    @Test
    void aWrongCompletedTypeFollowsTheExistingMalformedBodyConvention() throws Exception {
        mockMvc.perform(put("/api/assets/{assetId}/playback-progress", assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionMs\":12345,\"completed\":\"sometimes\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(playbackProgress);
    }

    @Test
    void nonNumericPositionIsRejectedBeforeReachingTheApplicationLayer() throws Exception {
        mockMvc.perform(put("/api/assets/{assetId}/playback-progress", assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionMs\":\"not-a-number\",\"completed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(playbackProgress);
    }

    @Test
    void missingOrUnauthorizedAssetKeepsTheSafeNotFoundBehaviourOnBothVerbs() throws Exception {
        when(playbackProgress.getProgress(assetId)).thenThrow(new AssetNotFoundException());
        when(playbackProgress.saveProgress(eq(assetId), any())).thenThrow(new AssetNotFoundException());

        mockMvc.perform(get("/api/assets/{assetId}/playback-progress", assetId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Asset not found"));

        mockMvc.perform(put("/api/assets/{assetId}/playback-progress", assetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionMs\":10,\"completed\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.containsString("select"))));
    }
}
