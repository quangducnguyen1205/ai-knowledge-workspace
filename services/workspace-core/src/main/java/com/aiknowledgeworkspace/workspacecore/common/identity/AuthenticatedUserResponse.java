package com.aiknowledgeworkspace.workspacecore.common.identity;

import java.util.UUID;

public record AuthenticatedUserResponse(UUID id, String email) {
}
