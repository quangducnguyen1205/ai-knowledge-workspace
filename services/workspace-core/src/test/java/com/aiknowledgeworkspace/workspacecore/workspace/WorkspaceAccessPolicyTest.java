package com.aiknowledgeworkspace.workspacecore.workspace;

import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;

import com.aiknowledgeworkspace.workspacecore.workspace.application.service.WorkspaceAccessPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.identity.adapter.in.security.CurrentUserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceAccessPolicyTest {

    @Mock
    private CurrentUserService currentUserService;

    @Test
    void isOwnedByCurrentUserRequiresMatchingOwner() {
        WorkspaceAccessPolicy policy = new WorkspaceAccessPolicy(currentUserService);
        Workspace workspace = new Workspace(UUID.randomUUID(), "Algorithms", "user-1", false);

        when(currentUserService.getCurrentUserId()).thenReturn("user-1");

        assertThat(policy.isOwnedByCurrentUser(workspace)).isTrue();
    }

    @Test
    void isOwnedByCurrentUserRejectsDifferentOwnerOrMissingOwner() {
        WorkspaceAccessPolicy policy = new WorkspaceAccessPolicy(currentUserService);
        Workspace otherWorkspace = new Workspace(UUID.randomUUID(), "Algorithms", "user-2", false);
        Workspace unownedWorkspace = new Workspace(UUID.randomUUID(), "Unowned", null, false);

        when(currentUserService.getCurrentUserId()).thenReturn("user-1");

        assertThat(policy.isOwnedByCurrentUser(otherWorkspace)).isFalse();
        assertThat(policy.isOwnedByCurrentUser(unownedWorkspace)).isFalse();
    }
}
