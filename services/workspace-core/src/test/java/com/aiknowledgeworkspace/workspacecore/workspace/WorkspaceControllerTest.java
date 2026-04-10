package com.aiknowledgeworkspace.workspacecore.workspace;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class WorkspaceControllerTest {

    private WorkspaceService workspaceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        workspaceService = mock(WorkspaceService.class);
        WorkspaceController workspaceController = new WorkspaceController(workspaceService);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(workspaceController)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createWorkspaceReturnsCreatedWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = workspace(workspaceId, "Algorithms", Instant.parse("2026-04-03T08:00:00Z"));
        when(workspaceService.createWorkspace("Algorithms")).thenReturn(workspace);

        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Algorithms"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(workspaceId.toString()))
                .andExpect(jsonPath("$.name").value("Algorithms"))
                .andExpect(jsonPath("$.createdAt").value("2026-04-03T08:00:00Z"));
    }

    @Test
    void createWorkspaceRejectsBlankName() throws Exception {
        when(workspaceService.createWorkspace("   "))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Workspace name is required"));

        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Workspace name is required"));
    }

    @Test
    void listWorkspacesReturnsWorkspaceSummaries() throws Exception {
        Workspace defaultWorkspace = workspace(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Default Workspace",
                Instant.parse("2026-04-03T08:00:00Z")
        );
        Workspace courseWorkspace = workspace(
                UUID.randomUUID(),
                "Distributed Systems",
                Instant.parse("2026-04-03T09:00:00Z")
        );
        when(workspaceService.listWorkspaces()).thenReturn(List.of(defaultWorkspace, courseWorkspace));

        mockMvc.perform(get("/api/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(defaultWorkspace.getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Default Workspace"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-04-03T08:00:00Z"))
                .andExpect(jsonPath("$[1].id").value(courseWorkspace.getId().toString()))
                .andExpect(jsonPath("$[1].name").value("Distributed Systems"))
                .andExpect(jsonPath("$[1].createdAt").value("2026-04-03T09:00:00Z"));
    }

    @Test
    void getWorkspaceReturnsWorkspaceDetails() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = workspace(workspaceId, "Operating Systems", Instant.parse("2026-04-03T10:00:00Z"));
        when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);

        mockMvc.perform(get("/api/workspaces/{workspaceId}", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workspaceId.toString()))
                .andExpect(jsonPath("$.name").value("Operating Systems"))
                .andExpect(jsonPath("$.createdAt").value("2026-04-03T10:00:00Z"));
    }

    @Test
    void getWorkspaceReturnsStructuredBadRequestForMalformedWorkspaceId() throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"))
                .andExpect(jsonPath("$.message").value("workspaceId must be a valid UUID"));
    }

    @Test
    void getWorkspaceReturnsStructuredNotFoundForUnknownWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceService.getWorkspace(workspaceId))
                .thenThrow(new WorkspaceNotFoundException(workspaceId));

        mockMvc.perform(get("/api/workspaces/{workspaceId}", workspaceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Workspace not found: " + workspaceId));
    }

    private Workspace workspace(UUID id, String name, Instant createdAt) {
        Workspace workspace = new Workspace(id, name);
        org.springframework.test.util.ReflectionTestUtils.setField(workspace, "createdAt", createdAt);
        return workspace;
    }
}
