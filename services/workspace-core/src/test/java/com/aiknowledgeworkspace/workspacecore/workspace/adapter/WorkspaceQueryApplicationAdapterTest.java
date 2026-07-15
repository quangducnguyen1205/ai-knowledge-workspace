package com.aiknowledgeworkspace.workspacecore.workspace.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.application.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.application.internal.WorkspaceService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceQueryApplicationAdapterTest {

    private final WorkspaceService workspaceService = mock(WorkspaceService.class);
    private final WorkspaceQueryApplicationAdapter adapter = new WorkspaceQueryApplicationAdapter(workspaceService);

    @Test
    void exposesOnlyNeutralWorkspaceAccessFacts() {
        UUID workspaceId = UUID.randomUUID();
        Workspace workspace = new Workspace(workspaceId, "Algorithms", "user-1", false);
        when(workspaceService.resolveWorkspaceOrDefault(workspaceId)).thenReturn(workspace);

        WorkspaceAccess result = adapter.resolveWorkspaceOrDefault(workspaceId);

        assertThat(result).isEqualTo(new WorkspaceAccess(workspaceId, "user-1"));
    }

    @Test
    void delegatesWorkspaceIdResolution() {
        UUID requestedId = UUID.randomUUID();
        UUID resolvedId = UUID.randomUUID();
        when(workspaceService.resolveWorkspaceOrDefault(requestedId))
                .thenReturn(new Workspace(resolvedId, "Default", "user-1", true));

        assertThat(adapter.resolveWorkspaceId(requestedId)).isEqualTo(resolvedId);
    }

    @Test
    void delegatesOwnershipCheckByIdentifier() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceService.isOwnedByCurrentUser(workspaceId)).thenReturn(true);

        assertThat(adapter.isOwnedByCurrentUser(workspaceId)).isTrue();
        verify(workspaceService).isOwnedByCurrentUser(workspaceId);
    }
}
