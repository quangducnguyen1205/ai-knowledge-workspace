package com.aiknowledgeworkspace.workspacecore.common.identity;

public record OidcJwtIdentity(
        String identityProvider,
        String externalSubject,
        String email
) {
}
