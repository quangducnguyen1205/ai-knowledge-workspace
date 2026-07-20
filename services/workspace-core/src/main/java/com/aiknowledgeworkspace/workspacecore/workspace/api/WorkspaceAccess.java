package com.aiknowledgeworkspace.workspacecore.workspace.api;

import java.util.UUID;

public record WorkspaceAccess(UUID workspaceId, String ownerId) {
}
