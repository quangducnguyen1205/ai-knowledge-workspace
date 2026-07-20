package com.aiknowledgeworkspace.workspacecore.identity.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.identity.application.port.out.UserAccountStore;
import com.aiknowledgeworkspace.workspacecore.identity.domain.UserAccount;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:keycloak-jwt-security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "workspace.security.authentication-mode=keycloak_jwt",
        "workspace.security.oidc.issuer-uri=https://keycloak.example.test/realms/project3",
        "workspace.security.oidc.jwk-set-uri=http://localhost/not-used-in-this-test",
        "workspace.security.oidc.audience=workspace-core"
})
@AutoConfigureMockMvc
class KeycloakJwtSecurityIntegrationTest {

    private static final String ISSUER = "https://keycloak.example.test/realms/project3";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountStore userAccountRepository;

    @Autowired
    private WorkspaceStore workspaceRepository;

    @Test
    void validJwtCanReadCurrentProductUserAndProvisionLocalIdentity() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me").with(jwt().jwt(jwtFor("subject-1", "learner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("learner@example.com"))
                .andReturn();

        String userId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        UserAccount user = userAccountRepository.findById(java.util.UUID.fromString(userId)).orElseThrow();

        assertThat(user.getIdentityProvider()).isEqualTo(ISSUER);
        assertThat(user.getExternalSubject()).isEqualTo("subject-1");
        assertThat(workspaceRepository.findOwnedDefaults(userId)).hasSize(1);
    }

    @Test
    void repeatedJwtResolvesSameCurrentProductUser() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/me").with(jwt().jwt(jwtFor("subject-repeat", "repeat@example.com"))))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/me").with(jwt().jwt(jwtFor("subject-repeat", "changed@example.com"))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(readId(first)).isEqualTo(readId(second));
    }

    @Test
    void differentJwtSubjectsDoNotShareProductIdentity() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/me").with(jwt().jwt(jwtFor("subject-a", "same@example.com"))))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/me").with(jwt().jwt(jwtFor("subject-b", "same@example.com"))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(readId(first)).isNotEqualTo(readId(second));
    }

    @Test
    void legacySessionOnlyRequestIsRejectedInKeycloakJwtMode() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CURRENT_USER_ID", java.util.UUID.randomUUID().toString());

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyLoginEndpointIsUnavailableInKeycloakJwtMode() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_MODE_UNAVAILABLE"));
    }

    @Test
    void workspaceOwnershipStillUsesSpringProductUser() throws Exception {
        MvcResult createWorkspaceResult = mockMvc.perform(post("/api/workspaces")
                        .with(jwt().jwt(jwtFor("owner-subject", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Private workspace"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String workspaceId = com.jayway.jsonpath.JsonPath.read(
                createWorkspaceResult.getResponse().getContentAsString(),
                "$.id"
        );

        mockMvc.perform(get("/api/workspaces/{workspaceId}", workspaceId)
                        .with(jwt().jwt(jwtFor("other-subject", "other@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void issuerMismatchIsRejectedBeforeProductIdentityIsResolved() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(jwtFor(
                        "subject-bad-issuer",
                        "bad-issuer@example.com",
                        "https://other.example.test/realms/project3",
                        List.of("workspace-core")
                ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void audienceMismatchIsRejectedWhenAudienceIsConfigured() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(jwtFor(
                        "subject-bad-audience",
                        "bad-audience@example.com",
                        ISSUER,
                        List.of("another-client")
                ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private java.util.function.Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> jwtFor(
            String subject,
            String email
    ) {
        return jwtFor(subject, email, ISSUER, List.of("workspace-core"));
    }

    private java.util.function.Consumer<Jwt.Builder> jwtFor(
            String subject,
            String email,
            String issuer,
            List<String> audience
    ) {
        return jwt -> jwt
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .claim("email", email);
    }

    private String readId(MvcResult result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
