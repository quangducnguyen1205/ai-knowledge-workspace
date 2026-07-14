package com.aiknowledgeworkspace.workspacecore.workspace;

import com.aiknowledgeworkspace.workspacecore.workspace.infrastructure.persistence.WorkspaceRepository;

import com.aiknowledgeworkspace.workspacecore.common.identity.provisioning.DefaultWorkspaceProvisioner;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class DefaultWorkspaceProvisionerAdapter implements DefaultWorkspaceProvisioner {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceProperties workspaceProperties;

    DefaultWorkspaceProvisionerAdapter(
            WorkspaceRepository workspaceRepository,
            WorkspaceProperties workspaceProperties
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceProperties = workspaceProperties;
    }

    @Override
    public void provisionFor(UUID userId) {
        workspaceRepository.save(new Workspace(
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
