package com.aiknowledgeworkspace.workspacecore.identity.application.result;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email) {
}
