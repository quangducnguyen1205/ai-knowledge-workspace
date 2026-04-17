package com.aiknowledgeworkspace.workspacecore.common.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    private CurrentUserService currentUserService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        currentUserService = new CurrentUserService(new CurrentUserProperties());
        authService = new AuthService(
                userAccountRepository,
                currentUserService,
                new BCryptPasswordEncoder()
        );
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void registerCreatesUserAndEstablishesSession() {
        MockHttpSession session = new MockHttpSession();
        UUID userId = UUID.randomUUID();

        when(userAccountRepository.findByEmail("learner@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount userAccount = invocation.getArgument(0);
            ReflectionTestUtils.setField(userAccount, "id", userId);
            return userAccount;
        });

        AuthenticatedUserResponse response = authService.register(
                new RegisterRequest("  Learner@Example.com  ", "password123"),
                session
        );

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(userCaptor.capture());

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("learner@example.com");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("learner@example.com");
        assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo("password123");
        assertThat(session.getAttribute(currentUserService.getSessionAttributeName())).isEqualTo(userId.toString());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userAccountRepository.findByEmail("learner@example.com"))
                .thenReturn(Optional.of(existingUser(UUID.randomUUID(), "learner@example.com", "hash")));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("learner@example.com", "password123"),
                new MockHttpSession()
        ))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Email is already registered");
    }

    @Test
    void registerTranslatesDuplicateEmailRaceIntoConflict() {
        when(userAccountRepository.findByEmail("learner@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("learner@example.com", "password123"),
                new MockHttpSession()
        ))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Email is already registered");
    }

    @Test
    void loginEstablishesSessionWhenCredentialsMatch() {
        MockHttpSession session = new MockHttpSession();
        UUID userId = UUID.randomUUID();
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        UserAccount userAccount = existingUser(userId, "learner@example.com", passwordEncoder.encode("password123"));
        authService = new AuthService(userAccountRepository, currentUserService, passwordEncoder);

        when(userAccountRepository.findByEmail("learner@example.com")).thenReturn(Optional.of(userAccount));

        AuthenticatedUserResponse response = authService.login(
                new LoginRequest(" Learner@Example.com ", "password123"),
                session
        );

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("learner@example.com");
        assertThat(session.getAttribute(currentUserService.getSessionAttributeName())).isEqualTo(userId.toString());
    }

    @Test
    void loginRejectsInvalidCredentials() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userAccountRepository, currentUserService, passwordEncoder);
        when(userAccountRepository.findByEmail("learner@example.com")).thenReturn(Optional.of(
                existingUser(UUID.randomUUID(), "learner@example.com", passwordEncoder.encode("other-password"))
        ));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("learner@example.com", "password123"),
                new MockHttpSession()
        ))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is incorrect");
    }

    @Test
    void getCurrentUserReturnsAuthenticatedSessionUser() {
        UUID userId = UUID.randomUUID();
        UserAccount userAccount = existingUser(userId, "learner@example.com", "hash");
        bindSessionUser(userId.toString());
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(userAccount));

        AuthenticatedUserResponse response = authService.getCurrentUser();

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("learner@example.com");
    }

    @Test
    void getCurrentUserRejectsWhenNoAuthenticatedSessionExists() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThatThrownBy(() -> authService.getCurrentUser())
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Authentication is required");
    }

    @Test
    void logoutClearsSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(currentUserService.getSessionAttributeName(), UUID.randomUUID().toString());

        authService.logout(session);

        assertThat(session.isInvalid()).isTrue();
    }

    private UserAccount existingUser(UUID userId, String email, String passwordHash) {
        UserAccount userAccount = new UserAccount(email, passwordHash);
        ReflectionTestUtils.setField(userAccount, "id", userId);
        return userAccount;
    }

    private void bindSessionUser(String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        currentUserService.establishCurrentUser(session, userId);
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
