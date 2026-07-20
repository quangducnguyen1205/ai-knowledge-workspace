package com.aiknowledgeworkspace.workspacecore.workspace;

import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultWorkspaceProvisionerAdapterTest {

    @Test
    void preservesDeterministicDefaultWorkspaceIdentityAndOwnership() {
        WorkspaceStore repository = mock(WorkspaceStore.class);
        WorkspaceProperties properties = new WorkspaceProperties();
        properties.setDefaultName("Default Workspace");
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        new DefaultWorkspaceProvisionerAdapter(repository, properties).provisionFor(userId);

        ArgumentCaptor<Workspace> workspace = ArgumentCaptor.forClass(Workspace.class);
        verify(repository).save(workspace.capture());
        assertThat(workspace.getValue().getId()).isEqualTo(UUID.nameUUIDFromBytes(
                ("default-workspace:" + userId).getBytes(StandardCharsets.UTF_8)
        ));
        assertThat(workspace.getValue().getName()).isEqualTo("Default Workspace");
        assertThat(workspace.getValue().getOwnerId()).isEqualTo(userId.toString());
        assertThat(workspace.getValue().isDefaultWorkspace()).isTrue();
    }
}
