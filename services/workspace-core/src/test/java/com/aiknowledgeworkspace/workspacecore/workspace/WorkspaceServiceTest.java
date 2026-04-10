package com.aiknowledgeworkspace.workspacecore.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Test
    void listWorkspacesEnsuresDefaultWorkspaceBeforeReadingAll() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(workspaceRepository, workspaceProperties);

        Workspace defaultWorkspace = workspace(
                workspaceProperties.getDefaultId(),
                workspaceProperties.getDefaultName(),
                Instant.parse("2026-04-03T08:00:00Z")
        );
        Workspace secondWorkspace = workspace(
                UUID.randomUUID(),
                "Algorithms",
                Instant.parse("2026-04-03T09:00:00Z")
        );

        when(workspaceRepository.findById(workspaceProperties.getDefaultId())).thenReturn(Optional.of(defaultWorkspace));
        when(workspaceRepository.findAll(any(Sort.class))).thenReturn(List.of(defaultWorkspace, secondWorkspace));

        List<Workspace> workspaces = workspaceService.listWorkspaces();

        assertThat(workspaces).containsExactly(defaultWorkspace, secondWorkspace);
        verify(workspaceRepository).findAll(eq(Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("name")
        )));
    }

    @Test
    void getWorkspaceReturnsDefaultWorkspaceWhenDefaultWorkspaceIdIsRequested() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(workspaceRepository, workspaceProperties);
        Workspace defaultWorkspace = workspace(
                workspaceProperties.getDefaultId(),
                workspaceProperties.getDefaultName(),
                Instant.parse("2026-04-03T08:00:00Z")
        );

        when(workspaceRepository.findById(workspaceProperties.getDefaultId())).thenReturn(Optional.of(defaultWorkspace));

        Workspace result = workspaceService.getWorkspace(workspaceProperties.getDefaultId());

        assertThat(result.getId()).isEqualTo(workspaceProperties.getDefaultId());
        assertThat(result.getName()).isEqualTo(workspaceProperties.getDefaultName());
    }

    @Test
    void createWorkspaceTrimsAndPersistsName() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(workspaceRepository, workspaceProperties);
        Workspace savedWorkspace = workspace(UUID.randomUUID(), "Algorithms", Instant.parse("2026-04-03T08:00:00Z"));

        when(workspaceRepository.save(any(Workspace.class))).thenReturn(savedWorkspace);

        Workspace result = workspaceService.createWorkspace("  Algorithms  ");

        assertThat(result).isSameAs(savedWorkspace);

        ArgumentCaptor<Workspace> workspaceCaptor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).save(workspaceCaptor.capture());
        assertThat(workspaceCaptor.getValue().getName()).isEqualTo("Algorithms");
    }

    @Test
    void createWorkspaceRejectsBlankName() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(workspaceRepository, workspaceProperties);

        assertThatThrownBy(() -> workspaceService.createWorkspace("   "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Workspace name is required");
    }

    private Workspace workspace(UUID id, String name, Instant createdAt) {
        Workspace workspace = new Workspace(id, name);
        org.springframework.test.util.ReflectionTestUtils.setField(workspace, "createdAt", createdAt);
        return workspace;
    }
}
