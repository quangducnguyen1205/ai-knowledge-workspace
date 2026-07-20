package com.aiknowledgeworkspace.workspacecore.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.identity.application.command.LoginUserCommand;
import com.aiknowledgeworkspace.workspacecore.identity.application.command.RegisterUserCommand;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.AuthenticationRequiredException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.EmailAlreadyRegisteredException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.InvalidCredentialsException;
import com.aiknowledgeworkspace.workspacecore.identity.application.port.out.UserAccountConflictException;
import com.aiknowledgeworkspace.workspacecore.identity.application.port.out.UserAccountStore;
import com.aiknowledgeworkspace.workspacecore.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    @Mock
    private UserAccountStore userAccountStore;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthApplicationService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AuthApplicationService(userAccountStore, passwordEncoder);
    }

    @Test
    void registerNormalizesCredentialsAndCreatesUser() {
        UUID userId = UUID.randomUUID();
        when(userAccountStore.findByEmail("learner@example.com")).thenReturn(Optional.empty());
        when(userAccountStore.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", userId);
            return user;
        });

        var result = service.register(new RegisterUserCommand("  Learner@Example.com  ", "password123"));

        ArgumentCaptor<UserAccount> savedUser = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountStore).save(savedUser.capture());
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("learner@example.com");
        assertThat(savedUser.getValue().getPasswordHash()).isNotEqualTo("password123");
    }

    @Test
    void registerRejectsExistingEmail() {
        when(userAccountStore.findByEmail("learner@example.com"))
                .thenReturn(Optional.of(existingUser(UUID.randomUUID(), "learner@example.com", "hash")));

        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("learner@example.com", "password123")
        )).isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void registerTranslatesPersistenceRaceIntoStableConflict() {
        when(userAccountStore.findByEmail("learner@example.com")).thenReturn(Optional.empty());
        when(userAccountStore.save(any(UserAccount.class)))
                .thenThrow(new UserAccountConflictException(new IllegalStateException("duplicate key")));

        assertThatThrownBy(() -> service.register(
                new RegisterUserCommand("learner@example.com", "password123")
        )).isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Email is already registered");
    }

    @Test
    void loginReturnsUserWhenPasswordMatches() {
        UUID userId = UUID.randomUUID();
        UserAccount user = existingUser(
                userId,
                "learner@example.com",
                passwordEncoder.encode("password123")
        );
        when(userAccountStore.findByEmail("learner@example.com")).thenReturn(Optional.of(user));

        var result = service.login(new LoginUserCommand(" Learner@Example.com ", "password123"));

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("learner@example.com");
    }

    @Test
    void loginRejectsInvalidCredentials() {
        when(userAccountStore.findByEmail("learner@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(
                new LoginUserCommand("learner@example.com", "password123")
        )).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void getUserRequiresValidPersistedIdentity() {
        UUID userId = UUID.randomUUID();
        when(userAccountStore.findById(userId))
                .thenReturn(Optional.of(existingUser(userId, "learner@example.com", "hash")));

        assertThat(service.getUser(userId.toString()).email()).isEqualTo("learner@example.com");
        assertThatThrownBy(() -> service.getUser(null))
                .isInstanceOf(AuthenticationRequiredException.class);
        assertThatThrownBy(() -> service.getUser("not-a-uuid"))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    private UserAccount existingUser(UUID userId, String email, String passwordHash) {
        UserAccount user = new UserAccount(email, passwordHash);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
