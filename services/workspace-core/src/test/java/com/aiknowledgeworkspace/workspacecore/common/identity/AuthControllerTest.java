package com.aiknowledgeworkspace.workspacecore.common.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiExceptionHandler;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private CurrentUserProperties currentUserProperties;
    private UserAccountRepository userAccountRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        currentUserProperties = new CurrentUserProperties();
        userAccountRepository = Mockito.mock(UserAccountRepository.class);
        CurrentUserService currentUserService = new CurrentUserService(currentUserProperties);
        AuthService authService = new AuthService(
                userAccountRepository,
                currentUserService,
                new BCryptPasswordEncoder()
        );
        AuthController authController = new AuthController(authService, currentUserService);

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createSessionStoresCurrentUserInHttpSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "  study-user-1  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("study-user-1"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(currentUserProperties.getSessionAttributeName()))
                .isEqualTo("study-user-1");
    }

    @Test
    void registerReturnsCreatedUserAndStoresSession() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userAccountRepository.findByEmail("learner@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount userAccount = invocation.getArgument(0);
            ReflectionTestUtils.setField(userAccount, "id", userId);
            return userAccount;
        });

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "  Learner@Example.com  ",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("learner@example.com"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(currentUserProperties.getSessionAttributeName()))
                .isEqualTo(userId.toString());
    }

    @Test
    void registerReturnsConflictForDuplicateEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount existingUser = new UserAccount("learner@example.com", "hash");
        ReflectionTestUtils.setField(existingUser, "id", userId);
        when(userAccountRepository.findByEmail("learner@example.com")).thenReturn(Optional.of(existingUser));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void registerReturnsValidationErrorForMissingBody() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AUTH_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is required"));
    }

    @Test
    void registerReturnsValidationErrorForInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("email must be a valid email address"));
    }

    @Test
    void registerReturnsValidationErrorForShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.message").value("password must be at least 8 characters"));
    }

    @Test
    void loginReturnsAuthenticatedUserAndStoresSession() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount userAccount = new UserAccount(
                "learner@example.com",
                new BCryptPasswordEncoder().encode("password123")
        );
        ReflectionTestUtils.setField(userAccount, "id", userId);
        when(userAccountRepository.findByEmail("learner@example.com")).thenReturn(Optional.of(userAccount));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " learner@example.com ",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("learner@example.com"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(currentUserProperties.getSessionAttributeName()))
                .isEqualTo(userId.toString());
    }

    @Test
    void loginReturnsValidationErrorForMissingBody() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AUTH_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is required"));
    }

    @Test
    void loginReturnsUnauthorizedForInvalidCredentials() throws Exception {
        when(userAccountRepository.findByEmail("learner@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));
    }

    @Test
    void loginSessionCanBeUsedToReadCurrentUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount userAccount = new UserAccount(
                "learner@example.com",
                new BCryptPasswordEncoder().encode("password123")
        );
        ReflectionTestUtils.setField(userAccount, "id", userId);
        when(userAccountRepository.findByEmail("learner@example.com")).thenReturn(Optional.of(userAccount));
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(userAccount));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "learner@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("learner@example.com"));
    }

    @Test
    void logoutReturnsNoContentAndInvalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(currentUserProperties.getSessionAttributeName(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void getCurrentUserReturnsAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UserAccount userAccount = new UserAccount("learner@example.com", "hash");
        ReflectionTestUtils.setField(userAccount, "id", userId);
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(userAccount));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(currentUserProperties.getSessionAttributeName(), userId.toString());

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("learner@example.com"));
    }

    @Test
    void getCurrentUserIgnoresHeaderFallbackWithoutAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header(currentUserProperties.getHeaderName(), "header-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void getCurrentUserReturnsUnauthorizedWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void createSessionRejectsBlankUserId() throws Exception {
        mockMvc.perform(post("/api/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_USER_ID"))
                .andExpect(jsonPath("$.message").value("userId is required"));
    }

    @Test
    void createSessionRejectsMissingBody() throws Exception {
        mockMvc.perform(post("/api/auth/session")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_USER_ID"))
                .andExpect(jsonPath("$.message").value("userId is required"));
    }
}
