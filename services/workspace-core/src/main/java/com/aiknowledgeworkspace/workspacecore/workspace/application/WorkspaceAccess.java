package com.aiknowledgeworkspace.workspacecore.workspace.application;

import java.util.UUID;

public record WorkspaceAccess(UUID workspaceId, String ownerId) {
}
