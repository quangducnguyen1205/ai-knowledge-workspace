package com.aiknowledgeworkspace.workspacecore.identity.application.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.security")
public class WorkspaceSecurityProperties {

    private AuthenticationMode authenticationMode = AuthenticationMode.LEGACY_SESSION;
    private Oidc oidc = new Oidc();

    public AuthenticationMode getAuthenticationMode() {
        return authenticationMode;
    }

    public void setAuthenticationMode(AuthenticationMode authenticationMode) {
        this.authenticationMode = authenticationMode;
    }

    public Oidc getOidc() {
        return oidc;
    }

    public void setOidc(Oidc oidc) {
        this.oidc = oidc;
    }

    public boolean isLegacySessionMode() {
        return authenticationMode == AuthenticationMode.LEGACY_SESSION;
    }

    public boolean isKeycloakJwtMode() {
        return authenticationMode == AuthenticationMode.KEYCLOAK_JWT;
    }

    public static class Oidc {

        private String issuerUri;
        private String jwkSetUri;
        private String audience;

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }
}
