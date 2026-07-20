package com.aiknowledgeworkspace.workspacecore.workspace.adapter.in.provisioning;

import com.aiknowledgeworkspace.workspacecore.workspace.application.configuration.WorkspaceProperties;

import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;

import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;

import com.aiknowledgeworkspace.workspacecore.identity.application.port.out.workspace.DefaultWorkspaceProvisioner;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class DefaultWorkspaceProvisionerAdapter implements DefaultWorkspaceProvisioner {

    private final WorkspaceStore workspaceStore;
    private final WorkspaceProperties workspaceProperties;

    DefaultWorkspaceProvisionerAdapter(
            WorkspaceStore workspaceStore,
            WorkspaceProperties workspaceProperties
    ) {
        this.workspaceStore = workspaceStore;
        this.workspaceProperties = workspaceProperties;
    }

    @Override
    public void provisionFor(UUID userId) {
        workspaceStore.save(new Workspace(
                defaultWorkspaceIdFor(userId),
                workspaceProperties.getDefaultName(),
                userId.toString(),
                true
        ));
    }

    private UUID defaultWorkspaceIdFor(UUID userId) {
        return UUID.nameUUIDFromBytes(("default-workspace:" + userId).getBytes(StandardCharsets.UTF_8));
    }
}
