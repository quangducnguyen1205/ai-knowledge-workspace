package com.aiknowledgeworkspace.workspacecore.identity.adapter.in.security;

import com.aiknowledgeworkspace.workspacecore.identity.application.configuration.WorkspaceSecurityProperties;
import com.aiknowledgeworkspace.workspacecore.identity.application.configuration.AuthenticationMode;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.InvalidJwtIdentityException;
import com.aiknowledgeworkspace.workspacecore.identity.application.model.OidcJwtIdentity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OidcJwtIdentityMapperTest {

    private static final String ISSUER = "https://keycloak.example.test/realms/project3";

    @Test
    void mapsRequiredSubjectAndIssuerWithOptionalEmail() {
        WorkspaceSecurityProperties properties = securityProperties();
        properties.getOidc().setAudience("workspace-core");
        OidcJwtIdentityMapper mapper = new OidcJwtIdentityMapper(properties);

        OidcJwtIdentity identity = mapper.toIdentity(jwt(Map.of(
                "iss", ISSUER,
                "sub", "subject-1",
                "aud", List.of("workspace-core"),
                "email", " Learner@Example.com "
        )));

        assertThat(identity.identityProvider()).isEqualTo(ISSUER);
        assertThat(identity.externalSubject()).isEqualTo("subject-1");
        assertThat(identity.email()).isEqualTo("learner@example.com");
    }

    @Test
    void rejectsMissingSubject() {
        OidcJwtIdentityMapper mapper = new OidcJwtIdentityMapper(securityProperties());

        assertThatThrownBy(() -> mapper.toIdentity(jwt(Map.of("iss", ISSUER))))
                .isInstanceOf(InvalidJwtIdentityException.class)
                .hasMessage("JWT sub claim is required");
    }

    @Test
    void rejectsUnexpectedIssuer() {
        OidcJwtIdentityMapper mapper = new OidcJwtIdentityMapper(securityProperties());

        assertThatThrownBy(() -> mapper.toIdentity(jwt(Map.of(
                "iss", "https://other.example.test/realms/project3",
                "sub", "subject-1"
        ))))
                .isInstanceOf(InvalidJwtIdentityException.class)
                .hasMessage("JWT identity could not be resolved");
    }

    @Test
    void rejectsAudienceMismatchWhenAudienceIsConfigured() {
        WorkspaceSecurityProperties properties = securityProperties();
        properties.getOidc().setAudience("workspace-core");
        OidcJwtIdentityMapper mapper = new OidcJwtIdentityMapper(properties);

        assertThatThrownBy(() -> mapper.toIdentity(jwt(Map.of(
                "iss", ISSUER,
                "sub", "subject-1",
                "aud", List.of("another-client")
        ))))
                .isInstanceOf(InvalidJwtIdentityException.class)
                .hasMessage("JWT identity could not be resolved");
    }

    @Test
    void ignoresUnsafeEmailAsProfileCandidate() {
        OidcJwtIdentityMapper mapper = new OidcJwtIdentityMapper(securityProperties());

        OidcJwtIdentity identity = mapper.toIdentity(jwt(Map.of(
                "iss", ISSUER,
                "sub", "subject-1",
                "email", "not-an-email"
        )));

        assertThat(identity.email()).isNull();
    }

    private WorkspaceSecurityProperties securityProperties() {
        WorkspaceSecurityProperties properties = new WorkspaceSecurityProperties();
        properties.setAuthenticationMode(AuthenticationMode.KEYCLOAK_JWT);
        properties.getOidc().setIssuerUri(ISSUER);
        return properties;
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T01:00:00Z"),
                Map.of("alg", "none"),
                claims
        );
    }
}
