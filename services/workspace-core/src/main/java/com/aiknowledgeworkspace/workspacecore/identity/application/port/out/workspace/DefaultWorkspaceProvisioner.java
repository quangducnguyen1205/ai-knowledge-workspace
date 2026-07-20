package com.aiknowledgeworkspace.workspacecore.identity.application.port.out.workspace;

import java.util.UUID;

@FunctionalInterface
public interface DefaultWorkspaceProvisioner {
    void provisionFor(UUID userId);
}
