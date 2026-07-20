package com.aiknowledgeworkspace.workspacecore.assistant;

import com.aiknowledgeworkspace.workspacecore.assistant.application.port.in.AssistantContextQueryUseCase;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantCitation;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantContextResult;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantContextSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.common.identity.AuthenticationRequiredException;
import com.aiknowledgeworkspace.workspacecore.common.web.ApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceNotFoundException;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssistantContextControllerTest {

    private AssistantContextQueryUseCase assistantContextService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        assistantContextService = mock(AssistantContextQueryUseCase.class);
        AssistantContextController assistantContextController = new AssistantContextController(assistantContextService);
        ObjectMapper objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(assistantContextController)
                .setControllerAdvice(
                        new ApiExceptionHandler(),
                        new AssistantApiExceptionHandler(),
                        new WorkspaceApiExceptionHandler()
                )
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void contextEndpointReturnsRetrievalPackWithoutAnswerField() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(assistantContextService.query(any())).thenReturn(new AssistantContextResult(
                workspaceId,
                "dynamic programming",
                List.of(new AssistantContextSource(
                        assetId,
                        "Lecture",
                        "row-1",
                        1,
                        "2026-06-25T00:00:01Z",
                        "bounded transcript context",
                        new AssistantCitation(assetId, "row-1", 1)
                ))
        ));

        mockMvc.perform(post("/api/assistant/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": "%s",
                                  "query": "dynamic programming",
                                  "maxSources": 5,
                                  "contextWindow": 1
                                }
                                """.formatted(workspaceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.query").value("dynamic programming"))
                .andExpect(jsonPath("$.sources[0].assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.sources[0].assetTitle").value("Lecture"))
                .andExpect(jsonPath("$.sources[0].transcriptRowId").value("row-1"))
                .andExpect(jsonPath("$.sources[0].segmentIndex").value(1))
                .andExpect(jsonPath("$.sources[0].text").value("bounded transcript context"))
                .andExpect(jsonPath("$.sources[0].citation.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.sources[0].citation.transcriptRowId").value("row-1"))
                .andExpect(jsonPath("$.answer").doesNotExist())
                .andExpect(jsonPath("$.summary").doesNotExist());
    }

    @Test
    void invalidAssistantContextRequestReturnsStructuredBadRequest() throws Exception {
        when(assistantContextService.query(any()))
                .thenThrow(new InvalidAssistantContextRequestException(
                        "INVALID_ASSISTANT_QUERY",
                        "query is required"
                ));

        mockMvc.perform(post("/api/assistant/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": "%s",
                                  "query": "   "
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ASSISTANT_QUERY"))
                .andExpect(jsonPath("$.message").value("query is required"));
    }

    @Test
    void unauthenticatedCallerReturnsExistingUnauthorizedShape() throws Exception {
        when(assistantContextService.query(any()))
                .thenThrow(new AuthenticationRequiredException("Authentication is required"));

        mockMvc.perform(post("/api/assistant/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": "%s",
                                  "query": "query"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void nonOwnedWorkspaceReturnsExistingNotFoundShape() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(assistantContextService.query(any()))
                .thenThrow(new WorkspaceNotFoundException(workspaceId));

        mockMvc.perform(post("/api/assistant/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": "%s",
                                  "query": "query"
                                }
                                """.formatted(workspaceId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Workspace not found"));
    }
}
