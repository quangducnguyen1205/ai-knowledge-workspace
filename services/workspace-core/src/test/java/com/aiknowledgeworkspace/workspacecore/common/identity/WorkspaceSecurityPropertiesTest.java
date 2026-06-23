package com.aiknowledgeworkspace.workspacecore.common.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkspaceSecurityPropertiesTest {

    @Test
    void defaultsToLegacySessionMode() {
        WorkspaceSecurityProperties properties = new WorkspaceSecurityProperties();

        assertThat(properties.getAuthenticationMode()).isEqualTo(AuthenticationMode.LEGACY_SESSION);
        assertThat(properties.isLegacySessionMode()).isTrue();
        assertThat(properties.isKeycloakJwtMode()).isFalse();
    }
}
