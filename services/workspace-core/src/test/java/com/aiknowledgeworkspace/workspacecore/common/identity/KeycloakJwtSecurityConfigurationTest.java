package com.aiknowledgeworkspace.workspacecore.common.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KeycloakJwtSecurityConfigurationTest {

    @Test
    void jwtDecoderRequiresIssuerUriWhenKeycloakModeIsSelected() {
        WorkspaceSecurityProperties properties = new WorkspaceSecurityProperties();
        properties.setAuthenticationMode(AuthenticationMode.KEYCLOAK_JWT);
        properties.getOidc().setJwkSetUri("http://localhost/not-used");

        assertThatThrownBy(() -> new KeycloakJwtSecurityConfiguration().workspaceJwtDecoder(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("workspace.security.oidc.issuer-uri is required when workspace.security.authentication-mode=keycloak_jwt");
    }
}
