package com.aiknowledgeworkspace.workspacecore.workspace;

import com.aiknowledgeworkspace.workspacecore.asset.AssetRepository;
import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserProperties;
import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.data.domain.Sort;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private CurrentUserService currentUserService;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listWorkspacesReturnsOnlyCurrentUserWorkspacesAndEnsuresDefaultWorkspaceFirst() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "user-1";

        Workspace defaultWorkspace = workspace(
                workspaceProperties.getDefaultId(),
                workspaceProperties.getDefaultName(),
                currentUserId,
                true,
                Instant.parse("2026-04-03T08:00:00Z")
        );
        Workspace secondWorkspace = workspace(
                UUID.randomUUID(),
                "Algorithms",
                currentUserId,
                false,
                Instant.parse("2026-04-03T09:00:00Z")
        );

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(workspaceRepository.findAllByOwnerIdAndDefaultWorkspaceTrue(currentUserId)).thenReturn(List.of(defaultWorkspace));
        when(workspaceRepository.findByOwnerId(eq(currentUserId), any(Sort.class)))
                .thenReturn(List.of(defaultWorkspace, secondWorkspace));

        List<Workspace> workspaces = workspaceService.listWorkspaces();

        assertThat(workspaces).containsExactly(defaultWorkspace, secondWorkspace);
        InOrder inOrder = inOrder(workspaceRepository);
        inOrder.verify(workspaceRepository).findAllByOwnerIdAndDefaultWorkspaceTrue(currentUserId);
        inOrder.verify(workspaceRepository).findByOwnerId(eq(currentUserId), eq(workspaceListSort()));
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void getWorkspaceReturnsOwnedWorkspace() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "user-1";
        UUID workspaceId = UUID.randomUUID();
        Workspace ownedWorkspace = workspace(workspaceId, "Operating Systems", currentUserId, false,
                Instant.parse("2026-04-03T08:00:00Z"));

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(workspaceRepository.findByIdAndOwnerId(workspaceId, currentUserId)).thenReturn(Optional.of(ownedWorkspace));

        Workspace result = workspaceService.getWorkspace(workspaceId);

        assertThat(result).isSameAs(ownedWorkspace);
    }

    @Test
    void getWorkspaceRejectsNonOwnedWorkspaceAsNotFound() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "user-1";
        UUID workspaceId = UUID.randomUUID();

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(workspaceRepository.findByIdAndOwnerId(workspaceId, currentUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.getWorkspace(workspaceId))
                .isInstanceOf(WorkspaceNotFoundException.class)
                .hasMessage("Workspace not found: " + workspaceId);
    }

    @Test
    void createWorkspaceTrimsAndPersistsNameForCurrentUser() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "user-1";
        Workspace savedWorkspace = workspace(
                UUID.randomUUID(),
                "Algorithms",
                currentUserId,
                false,
                Instant.parse("2026-04-03T08:00:00Z")
        );

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(savedWorkspace);

        Workspace result = workspaceService.createWorkspace("  Algorithms  ");

        assertThat(result).isSameAs(savedWorkspace);

        ArgumentCaptor<Workspace> workspaceCaptor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).save(workspaceCaptor.capture());
        assertThat(workspaceCaptor.getValue().getName()).isEqualTo("Algorithms");
        assertThat(workspaceCaptor.getValue().getOwnerId()).isEqualTo(currentUserId);
        assertThat(workspaceCaptor.getValue().isDefaultWorkspace()).isFalse();
    }

    @Test
    void createWorkspaceRejectsBlankName() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );

        assertThatThrownBy(() -> workspaceService.createWorkspace("   "))
                .isInstanceOf(InvalidWorkspaceNameException.class)
                .hasMessageContaining("Workspace name is required");
    }

    @Test
    void createWorkspaceRejectsTooLongName() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );

        String tooLongName = "a".repeat(256);

        assertThatThrownBy(() -> workspaceService.createWorkspace(tooLongName))
                .isInstanceOf(InvalidWorkspaceNameException.class)
                .hasMessageContaining("Workspace name must be at most 255 characters");
    }

    @Test
    void createWorkspaceUsesCurrentUserFromSessionAuthEntry() {
        CurrentUserProperties currentUserProperties = new CurrentUserProperties();
        CurrentUserService realCurrentUserService = new CurrentUserService(currentUserProperties);
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                realCurrentUserService
        );
        Workspace savedWorkspace = workspace(
                UUID.randomUUID(),
                "Algorithms",
                "session-user",
                false,
                Instant.parse("2026-04-03T08:00:00Z")
        );

        bindSessionCurrentUser(realCurrentUserService, "session-user");
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(savedWorkspace);

        Workspace result = workspaceService.createWorkspace("Algorithms");

        assertThat(result).isSameAs(savedWorkspace);
        ArgumentCaptor<Workspace> workspaceCaptor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).save(workspaceCaptor.capture());
        assertThat(workspaceCaptor.getValue().getOwnerId()).isEqualTo("session-user");
    }

    @Test
    void listWorkspacesUsesCurrentUserFromSessionAuthEntry() {
        CurrentUserProperties currentUserProperties = new CurrentUserProperties();
        CurrentUserService realCurrentUserService = new CurrentUserService(currentUserProperties);
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                realCurrentUserService
        );
        String currentUserId = "session-user";
        Workspace defaultWorkspace = workspace(
                UUID.randomUUID(),
                workspaceProperties.getDefaultName(),
                currentUserId,
                true,
                Instant.parse("2026-04-03T08:00:00Z")
        );

        bindSessionCurrentUser(realCurrentUserService, currentUserId);
        when(workspaceRepository.findAllByOwnerIdAndDefaultWorkspaceTrue(currentUserId)).thenReturn(List.of(defaultWorkspace));
        when(workspaceRepository.findByOwnerId(eq(currentUserId), any(Sort.class)))
                .thenReturn(List.of(defaultWorkspace));

        List<Workspace> workspaces = workspaceService.listWorkspaces();

        assertThat(workspaces).containsExactly(defaultWorkspace);
        verify(workspaceRepository).findAllByOwnerIdAndDefaultWorkspaceTrue(currentUserId);
        verify(workspaceRepository).findByOwnerId(eq(currentUserId), eq(workspaceListSort()));
    }

    @Test
    void resolveWorkspaceOrDefaultCreatesDefaultWorkspaceForCurrentUserWhenMissing() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "user-2";
        Workspace defaultWorkspace = workspace(
                UUID.randomUUID(),
                workspaceProperties.getDefaultName(),
                currentUserId,
                true,
                Instant.parse("2026-04-03T08:00:00Z")
        );

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(currentUserService.isDefaultUser(currentUserId)).thenReturn(false);
        when(workspaceRepository.findAllByOwnerIdAndDefaultWorkspaceTrue(currentUserId)).thenReturn(List.of());
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(defaultWorkspace);

        Workspace result = workspaceService.resolveWorkspaceOrDefault(null);

        assertThat(result).isSameAs(defaultWorkspace);

        ArgumentCaptor<Workspace> workspaceCaptor = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).save(workspaceCaptor.capture());
        assertThat(workspaceCaptor.getValue().getOwnerId()).isEqualTo(currentUserId);
        assertThat(workspaceCaptor.getValue().isDefaultWorkspace()).isTrue();
        assertThat(workspaceCaptor.getValue().getName()).isEqualTo(workspaceProperties.getDefaultName());
        assertThat(workspaceCaptor.getValue().getId())
                .isEqualTo(UUID.nameUUIDFromBytes(("default-workspace:" + currentUserId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void resolveWorkspaceOrDefaultAdoptsLegacyDefaultWorkspaceForConfiguredDefaultUser() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "local-dev-user";
        Workspace legacyDefaultWorkspace = workspace(
                workspaceProperties.getDefaultId(),
                workspaceProperties.getDefaultName(),
                null,
                false,
                Instant.parse("2026-04-03T08:00:00Z")
        );
        Workspace adoptedWorkspace = workspace(
                workspaceProperties.getDefaultId(),
                workspaceProperties.getDefaultName(),
                currentUserId,
                true,
                Instant.parse("2026-04-03T08:00:00Z")
        );

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(currentUserService.isDefaultUser(currentUserId)).thenReturn(true);
        when(workspaceRepository.findAllByOwnerIdAndDefaultWorkspaceTrue(currentUserId)).thenReturn(List.of());
        when(workspaceRepository.findById(workspaceProperties.getDefaultId())).thenReturn(Optional.of(legacyDefaultWorkspace));
        when(workspaceRepository.save(legacyDefaultWorkspace)).thenReturn(adoptedWorkspace);

        Workspace result = workspaceService.resolveWorkspaceOrDefault(null);

        assertThat(result).isSameAs(adoptedWorkspace);
        assertThat(legacyDefaultWorkspace.getOwnerId()).isEqualTo(currentUserId);
        assertThat(legacyDefaultWorkspace.isDefaultWorkspace()).isTrue();
    }

    @Test
    void ensureDefaultWorkspaceRecoversFromConcurrentCreateRace() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "user-2";
        Workspace persistedDefaultWorkspace = workspace(
                UUID.nameUUIDFromBytes(("default-workspace:" + currentUserId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                workspaceProperties.getDefaultName(),
                currentUserId,
                true,
                Instant.parse("2026-04-03T08:00:00Z")
        );

        when(currentUserService.isDefaultUser(currentUserId)).thenReturn(false);
        when(workspaceRepository.findAllByOwnerIdAndDefaultWorkspaceTrue(currentUserId))
                .thenReturn(List.of(), List.of(persistedDefaultWorkspace));
        when(workspaceRepository.save(any(Workspace.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        Workspace result = workspaceService.ensureDefaultWorkspace(currentUserId);

        assertThat(result).isSameAs(persistedDefaultWorkspace);
    }

    @Test
    void ensureDefaultWorkspaceRejectsConfiguredDefaultWorkspaceIdOwnedByAnotherUser() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "local-dev-user";
        Workspace conflictingWorkspace = workspace(
                workspaceProperties.getDefaultId(),
                workspaceProperties.getDefaultName(),
                "another-user",
                true,
                Instant.parse("2026-04-03T08:00:00Z")
        );

        when(currentUserService.isDefaultUser(currentUserId)).thenReturn(true);
        when(workspaceRepository.findAllByOwnerIdAndDefaultWorkspaceTrue(currentUserId)).thenReturn(List.of());
        when(workspaceRepository.findById(workspaceProperties.getDefaultId())).thenReturn(Optional.of(conflictingWorkspace));

        assertThatThrownBy(() -> workspaceService.ensureDefaultWorkspace(currentUserId))
                .isInstanceOf(DefaultWorkspaceConflictException.class)
                .hasMessage("Configured default workspace ID is already owned by another user and cannot be adopted safely");
    }

    @Test
    void ensureDefaultWorkspaceRejectsMultipleOwnedDefaultWorkspaces() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "user-1";

        when(workspaceRepository.findAllByOwnerIdAndDefaultWorkspaceTrue(currentUserId)).thenReturn(List.of(
                workspace(UUID.randomUUID(), "Default Workspace", currentUserId, true,
                        Instant.parse("2026-04-03T08:00:00Z")),
                workspace(UUID.randomUUID(), "Default Workspace", currentUserId, true,
                        Instant.parse("2026-04-03T09:00:00Z"))
        ));

        assertThatThrownBy(() -> workspaceService.ensureDefaultWorkspace(currentUserId))
                .isInstanceOf(DefaultWorkspaceConflictException.class)
                .hasMessage("Multiple default workspaces exist for the current user");
    }

    @Test
    void updateWorkspaceTrimsAndPersistsNameForOwnedWorkspace() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "user-1";
        UUID workspaceId = UUID.randomUUID();
        Workspace ownedWorkspace = workspace(
                workspaceId,
                "Operating Systems",
                currentUserId,
                false,
                Instant.parse("2026-04-03T08:00:00Z")
        );

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(workspaceRepository.findByIdAndOwnerId(workspaceId, currentUserId)).thenReturn(Optional.of(ownedWorkspace));
        when(workspaceRepository.save(ownedWorkspace)).thenReturn(ownedWorkspace);

        Workspace result = workspaceService.updateWorkspace(workspaceId, "  Renamed Workspace  ");

        assertThat(result).isSameAs(ownedWorkspace);
        assertThat(ownedWorkspace.getName()).isEqualTo("Renamed Workspace");
        verify(workspaceRepository).save(ownedWorkspace);
    }

    @Test
    void updateWorkspaceRejectsBlankName() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );

        assertThatThrownBy(() -> workspaceService.updateWorkspace(UUID.randomUUID(), "   "))
                .isInstanceOf(InvalidWorkspaceNameException.class)
                .hasMessageContaining("Workspace name is required");
    }

    @Test
    void deleteWorkspaceDeletesOwnedEmptyNonDefaultWorkspace() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "user-1";
        UUID workspaceId = UUID.randomUUID();
        Workspace ownedWorkspace = workspace(
                workspaceId,
                "Algorithms",
                currentUserId,
                false,
                Instant.parse("2026-04-03T08:00:00Z")
        );

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(workspaceRepository.findByIdAndOwnerId(workspaceId, currentUserId)).thenReturn(Optional.of(ownedWorkspace));
        when(assetRepository.countByWorkspace_Id(workspaceId)).thenReturn(0L);

        workspaceService.deleteWorkspace(workspaceId);

        verify(workspaceRepository).delete(ownedWorkspace);
    }

    @Test
    void deleteWorkspaceRejectsDefaultWorkspace() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "user-1";
        UUID workspaceId = UUID.randomUUID();
        Workspace defaultWorkspace = workspace(
                workspaceId,
                "Default Workspace",
                currentUserId,
                true,
                Instant.parse("2026-04-03T08:00:00Z")
        );

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(workspaceRepository.findByIdAndOwnerId(workspaceId, currentUserId)).thenReturn(Optional.of(defaultWorkspace));

        assertThatThrownBy(() -> workspaceService.deleteWorkspace(workspaceId))
                .isInstanceOf(WorkspaceDeleteConflictException.class)
                .hasMessage("Default workspace cannot be deleted");

        verify(workspaceRepository, never()).delete(any());
        verify(assetRepository, never()).countByWorkspace_Id(any());
    }

    @Test
    void deleteWorkspaceRejectsWorkspaceThatStillHasAssets() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties();
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                assetRepository,
                workspaceProperties,
                currentUserService
        );
        String currentUserId = "user-1";
        UUID workspaceId = UUID.randomUUID();
        Workspace ownedWorkspace = workspace(
                workspaceId,
                "Algorithms",
                currentUserId,
                false,
                Instant.parse("2026-04-03T08:00:00Z")
        );

        when(currentUserService.getCurrentUserId()).thenReturn(currentUserId);
        when(workspaceRepository.findByIdAndOwnerId(workspaceId, currentUserId)).thenReturn(Optional.of(ownedWorkspace));
        when(assetRepository.countByWorkspace_Id(workspaceId)).thenReturn(2L);

        assertThatThrownBy(() -> workspaceService.deleteWorkspace(workspaceId))
                .isInstanceOf(WorkspaceDeleteConflictException.class)
                .hasMessage("Workspace cannot be deleted while it still contains assets");

        verify(workspaceRepository, never()).delete(any());
    }

    private Workspace workspace(UUID id, String name, String ownerId, boolean defaultWorkspace, Instant createdAt) {
        Workspace workspace = new Workspace(id, name, ownerId, defaultWorkspace);
        org.springframework.test.util.ReflectionTestUtils.setField(workspace, "createdAt", createdAt);
        return workspace;
    }

    private Sort workspaceListSort() {
        return Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("name")
        );
    }

    private void bindSessionCurrentUser(CurrentUserService currentUserService, String currentUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        currentUserService.establishCurrentUser(session, currentUserId);
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
