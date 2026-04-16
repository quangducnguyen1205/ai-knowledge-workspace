package com.aiknowledgeworkspace.workspacecore.common.identity;

import jakarta.servlet.http.HttpSession;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_PASSWORD_LENGTH = 255;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserAccountRepository userAccountRepository;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserAccountRepository userAccountRepository,
            CurrentUserService currentUserService,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthenticatedUserResponse register(RegisterRequest request, HttpSession session) {
        Credentials credentials = normalizeCredentials(request == null ? null : request.email(), request == null ? null : request.password());

        if (userAccountRepository.findByEmail(credentials.email()).isPresent()) {
            throw new EmailAlreadyRegisteredException("Email is already registered");
        }

        UserAccount userAccount = userAccountRepository.save(new UserAccount(
                credentials.email(),
                passwordEncoder.encode(credentials.password())
        ));

        currentUserService.establishCurrentUser(session, userAccount.getId().toString());
        return toResponse(userAccount);
    }

    public AuthenticatedUserResponse login(LoginRequest request, HttpSession session) {
        Credentials credentials = normalizeCredentials(request == null ? null : request.email(), request == null ? null : request.password());

        UserAccount userAccount = userAccountRepository.findByEmail(credentials.email())
                .filter(user -> passwordEncoder.matches(credentials.password(), user.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Email or password is incorrect"));

        currentUserService.establishCurrentUser(session, userAccount.getId().toString());
        return toResponse(userAccount);
    }

    public void logout(HttpSession session) {
        if (session != null) {
            currentUserService.clearCurrentUser(session);
        }
    }

    public AuthenticatedUserResponse getCurrentUser() {
        String authenticatedUserId = currentUserService.getAuthenticatedSessionUserId();
        if (!StringUtils.hasText(authenticatedUserId)) {
            throw new AuthenticationRequiredException("Authentication is required");
        }

        UUID userId;
        try {
            userId = UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationRequiredException("Authentication is required");
        }

        UserAccount userAccount = userAccountRepository.findById(userId)
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

    private AuthenticatedUserResponse toResponse(UserAccount userAccount) {
        return new AuthenticatedUserResponse(userAccount.getId(), userAccount.getEmail());
    }

    private record Credentials(String email, String password) {
    }
}
