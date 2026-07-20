package com.aiknowledgeworkspace.workspacecore.identity.adapter.in.web;

import java.util.UUID;

public record AuthenticatedUserResponse(UUID id, String email) {
}
