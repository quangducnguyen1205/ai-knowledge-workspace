package com.aiknowledgeworkspace.workspacecore.identity.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The identity boundary in the default {@code legacy_session} mode: only a session established by
 * register/login carries identity. A caller-supplied user-id header must never authenticate, never
 * override an authenticated session, and never provision anything.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:legacy-session-security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc
class LegacySessionSecurityIntegrationTest {

    private static final String FORGED_IDENTITY_HEADER = "X-Current-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkspaceStore workspaceRepository;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/workspaces"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void forgedHeaderForExistingUserDoesNotAuthenticateOrExposeData() throws Exception {
        RegisteredUser victim = registerUser("victim@example.com");
        mockMvc.perform(get("/api/workspaces").session(victim.session()))
                .andExpect(status().isOk());
        assertThat(workspaceRepository.findOwned(victim.id())).isNotEmpty();

        mockMvc.perform(get("/api/workspaces").header(FORGED_IDENTITY_HEADER, victim.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$[*].id").doesNotExist());

        mockMvc.perform(get("/api/me").header(FORGED_IDENTITY_HEADER, victim.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void forgedHeaderForInventedUserDoesNotProvisionUserOrWorkspace() throws Exception {
        String inventedUserId = "attacker-invented-user";

        mockMvc.perform(get("/api/workspaces").header(FORGED_IDENTITY_HEADER, inventedUserId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        assertThat(workspaceRepository.findOwned(inventedUserId)).isEmpty();
        assertThat(workspaceRepository.findOwnedDefaults(inventedUserId)).isEmpty();
    }

    @Test
    void authenticatedSessionIsNotOverriddenByIdentityHeader() throws Exception {
        RegisteredUser userA = registerUser("override-a@example.com");
        RegisteredUser userB = registerUser("override-b@example.com");

        mockMvc.perform(get("/api/me")
                        .session(userA.session())
                        .header(FORGED_IDENTITY_HEADER, userB.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userA.id()))
                .andExpect(jsonPath("$.email").value("override-a@example.com"));

        MvcResult baseline = mockMvc.perform(get("/api/workspaces").session(userA.session()))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult withForgedHeader = mockMvc.perform(get("/api/workspaces")
                        .session(userA.session())
                        .header(FORGED_IDENTITY_HEADER, userB.id()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(workspaceIds(withForgedHeader)).isEqualTo(workspaceIds(baseline));
    }

    @Test
    void registerLoginLogoutFlowEstablishesAndClearsIdentity() throws Exception {
        RegisteredUser user = registerUser("lifecycle@example.com");

        mockMvc.perform(get("/api/me").session(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id()));

        mockMvc.perform(post("/api/auth/logout").session(user.session()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me").session(user.session()))
                .andExpect(status().isUnauthorized());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "lifecycle@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id()))
                .andReturn();

        MockHttpSession loginSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(loginSession).isNotNull();
        mockMvc.perform(get("/api/me").session(loginSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.id()));
    }

    @Test
    void workspaceIsolationHoldsAcrossAuthenticatedUsers() throws Exception {
        RegisteredUser owner = registerUser("isolation-owner@example.com");
        RegisteredUser other = registerUser("isolation-other@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/workspaces")
                        .session(owner.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Private workspace"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String workspaceId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/workspaces/{workspaceId}", workspaceId)
                        .session(other.session())
                        .header(FORGED_IDENTITY_HEADER, owner.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void removedAuthSessionEndpointNoLongerExists() throws Exception {
        mockMvc.perform(post("/api/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "any-user"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    private RegisteredUser registerUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return new RegisteredUser(id, session);
    }

    private static List<String> workspaceIds(MvcResult result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
    }

    private record RegisteredUser(String id, MockHttpSession session) {
    }
}
