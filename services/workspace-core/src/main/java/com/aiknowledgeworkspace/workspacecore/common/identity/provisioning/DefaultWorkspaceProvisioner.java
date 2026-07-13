package com.aiknowledgeworkspace.workspacecore.common.identity.provisioning;

import java.util.UUID;

@FunctionalInterface
public interface DefaultWorkspaceProvisioner {
    void provisionFor(UUID userId);
}
