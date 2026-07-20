package com.aiknowledgeworkspace.workspacecore.assistant;

import com.aiknowledgeworkspace.workspacecore.assistant.application.port.in.AssistantAnswerCommandUseCase;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantAnswerCitation;
import com.aiknowledgeworkspace.workspacecore.assistant.application.model.AssistantAnswerResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssistantAnswerControllerTest {

    private AssistantAnswerCommandUseCase assistantAnswerService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        assistantAnswerService = mock(AssistantAnswerCommandUseCase.class);
        AssistantAnswerController assistantAnswerController = new AssistantAnswerController(assistantAnswerService);
        ObjectMapper objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(assistantAnswerController)
                .setControllerAdvice(new ApiExceptionHandler(), new AssistantApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void answerEndpointReturnsStructuredGroundedAnswer() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(assistantAnswerService.answer(any())).thenReturn(new AssistantAnswerResult(
                "Use memoization for overlapping subproblems.",
                List.of(new AssistantAnswerCitation(
                        "src-abc123",
                        assetId,
                        "Lecture",
                        "row-1",
                        1,
                        "2026-06-25T00:00:01Z"
                )),
                false
        ));

        mockMvc.perform(post("/api/assistant/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": "%s",
                                  "question": "How should I solve it?",
                                  "maxSources": 5,
                                  "contextWindow": 1
                                }
                                """.formatted(workspaceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Use memoization for overlapping subproblems."))
                .andExpect(jsonPath("$.insufficientContext").value(false))
                .andExpect(jsonPath("$.citations[0].sourceId").value("src-abc123"))
                .andExpect(jsonPath("$.citations[0].assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.citations[0].assetTitle").value("Lecture"))
                .andExpect(jsonPath("$.citations[0].transcriptRowId").value("row-1"))
                .andExpect(jsonPath("$.model").doesNotExist())
                .andExpect(jsonPath("$.provider").doesNotExist());
    }

    @Test
    void invalidAssistantAnswerRequestReturnsStructuredBadRequest() throws Exception {
        when(assistantAnswerService.answer(any()))
                .thenThrow(new InvalidAssistantContextRequestException(
                        "INVALID_ASSISTANT_QUESTION",
                        "question is required"
                ));

        mockMvc.perform(post("/api/assistant/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": "%s",
                                  "question": "   "
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ASSISTANT_QUESTION"))
                .andExpect(jsonPath("$.message").value("question is required"));
    }

    @Test
    void providerUnavailableReturnsStructuredServiceUnavailable() throws Exception {
        when(assistantAnswerService.answer(any()))
                .thenThrow(new AssistantProviderUnavailableException(
                        "FastAPI returned HTTP 500 from http://internal:8000 with raw response body"
                ));

        mockMvc.perform(post("/api/assistant/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": "%s",
                                  "question": "What did the lecture say?"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ASSISTANT_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Dịch vụ tạm thời chưa sẵn sàng. Vui lòng thử lại sau."))
                .andExpect(content().string(not(containsString("FastAPI"))))
                .andExpect(content().string(not(containsString("internal:8000"))))
                .andExpect(content().string(not(containsString("raw response body"))));
    }
}
