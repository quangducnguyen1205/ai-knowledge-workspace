package com.aiknowledgeworkspace.workspacecore.identity.application.service;

import com.aiknowledgeworkspace.workspacecore.identity.application.command.LoginUserCommand;
import com.aiknowledgeworkspace.workspacecore.identity.application.command.RegisterUserCommand;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.AuthenticationRequiredException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.EmailAlreadyRegisteredException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.InvalidAuthRequestException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.InvalidCredentialsException;
import com.aiknowledgeworkspace.workspacecore.identity.application.port.in.AuthUseCase;
import com.aiknowledgeworkspace.workspacecore.identity.application.port.out.UserAccountStore;
import com.aiknowledgeworkspace.workspacecore.identity.application.port.out.UserAccountConflictException;
import com.aiknowledgeworkspace.workspacecore.identity.application.result.AuthenticatedUser;
import com.aiknowledgeworkspace.workspacecore.identity.domain.UserAccount;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthApplicationService implements AuthUseCase {

    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_PASSWORD_LENGTH = 255;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserAccountStore userAccountStore;
    private final PasswordEncoder passwordEncoder;

    public AuthApplicationService(
            UserAccountStore userAccountStore,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountStore = userAccountStore;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthenticatedUser register(RegisterUserCommand command) {
        Credentials credentials = normalizeCredentials(
                command == null ? null : command.email(),
                command == null ? null : command.password()
        );

        if (userAccountStore.findByEmail(credentials.email()).isPresent()) {
            throw new EmailAlreadyRegisteredException("Email is already registered");
        }

        UserAccount userAccount;
        try {
            userAccount = userAccountStore.save(new UserAccount(
                    credentials.email(),
                    passwordEncoder.encode(credentials.password())
            ));
        } catch (UserAccountConflictException exception) {
            throw new EmailAlreadyRegisteredException("Email is already registered");
        }

        return toResponse(userAccount);
    }

    @Override
    public AuthenticatedUser login(LoginUserCommand command) {
        Credentials credentials = normalizeCredentials(
                command == null ? null : command.email(),
                command == null ? null : command.password()
        );

        UserAccount userAccount = userAccountStore.findByEmail(credentials.email())
                .filter(user -> passwordEncoder.matches(credentials.password(), user.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Email or password is incorrect"));

        return toResponse(userAccount);
    }

    @Override
    public AuthenticatedUser getUser(String authenticatedUserId) {
        if (!StringUtils.hasText(authenticatedUserId)) {
            throw new AuthenticationRequiredException("Authentication is required");
        }

        UUID userId;
        try {
            userId = UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationRequiredException("Authentication is required");
        }

        UserAccount userAccount = userAccountStore.findById(userId)
                .orElseThrow(() -> new AuthenticationRequiredException("Authentication is required"));

        return toResponse(userAccount);
    }

    private Credentials normalizeCredentials(String email, String password) {
        if (email == null && password == null) {
            throw new InvalidAuthRequestException("INVALID_AUTH_REQUEST", "Request body is required");
        }

        return new Credentials(normalizeEmail(email), validatePassword(password));
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new InvalidAuthRequestException("INVALID_EMAIL", "email is required");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > MAX_EMAIL_LENGTH) {
            throw new InvalidAuthRequestException(
                    "INVALID_EMAIL",
                    "email must be at most " + MAX_EMAIL_LENGTH + " characters"
            );
        }
        if (!looksLikeEmail(normalizedEmail)) {
            throw new InvalidAuthRequestException("INVALID_EMAIL", "email must be a valid email address");
        }
        return normalizedEmail;
    }

    private String validatePassword(String password) {
        if (password == null) {
            throw new InvalidAuthRequestException("INVALID_PASSWORD", "password is required");
        }
        if (password.isBlank()) {
            throw new InvalidAuthRequestException("INVALID_PASSWORD", "password is required");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidAuthRequestException(
                    "INVALID_PASSWORD",
                    "password must be at least " + MIN_PASSWORD_LENGTH + " characters"
            );
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidAuthRequestException(
                    "INVALID_PASSWORD",
                    "password must be at most " + MAX_PASSWORD_LENGTH + " characters"
            );
        }
        return password;
    }

    private boolean looksLikeEmail(String email) {
        int atIndex = email.indexOf('@');
        return atIndex > 0
                && atIndex == email.lastIndexOf('@')
                && atIndex < email.length() - 1;
    }

    private AuthenticatedUser toResponse(UserAccount userAccount) {
        return new AuthenticatedUser(userAccount.getId(), userAccount.getEmail());
    }

    private record Credentials(String email, String password) {
    }
}
