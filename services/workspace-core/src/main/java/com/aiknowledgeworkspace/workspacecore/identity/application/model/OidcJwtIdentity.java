package com.aiknowledgeworkspace.workspacecore.identity.application.model;

public record OidcJwtIdentity(
        String identityProvider,
        String externalSubject,
        String email
) {
}
