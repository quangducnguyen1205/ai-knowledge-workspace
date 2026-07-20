package com.aiknowledgeworkspace.workspacecore.identity.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.aiknowledgeworkspace.workspacecore.identity.adapter.in.security.CurrentUserService;
import com.aiknowledgeworkspace.workspacecore.identity.application.command.LoginUserCommand;
import com.aiknowledgeworkspace.workspacecore.identity.application.command.RegisterUserCommand;
import com.aiknowledgeworkspace.workspacecore.identity.application.configuration.WorkspaceSecurityProperties;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.AuthModeUnavailableException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.AuthenticationRequiredException;
import com.aiknowledgeworkspace.workspacecore.identity.application.port.in.AuthUseCase;
import com.aiknowledgeworkspace.workspacecore.identity.application.result.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthUseCase authUseCase;
    private final CurrentUserService currentUserService;
    private final WorkspaceSecurityProperties securityProperties;

    @Autowired
    public AuthController(
            AuthUseCase authUseCase,
            CurrentUserService currentUserService,
            WorkspaceSecurityProperties securityProperties
    ) {
        this.authUseCase = authUseCase;
        this.currentUserService = currentUserService;
        this.securityProperties = securityProperties;
    }

    public AuthController(AuthUseCase authUseCase, CurrentUserService currentUserService) {
        this(authUseCase, currentUserService, new WorkspaceSecurityProperties());
    }

    @PostMapping("/api/auth/session")
    public AuthSessionResponse createSession(
            @RequestBody(required = false) CreateAuthSessionRequest request,
            HttpServletRequest httpServletRequest
    ) {
        requireLegacySessionMode();
        if (!currentUserService.isDevFallbackEnabled()) {
            throw new AuthenticationRequiredException("Authentication is required");
        }

        HttpSession session = httpServletRequest.getSession(true);
        String currentUserId = currentUserService.establishCurrentUser(
                session,
                request == null ? null : request.userId()
        );
        return new AuthSessionResponse(currentUserId);
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthenticatedUserResponse register(
            @RequestBody(required = false) RegisterRequest request,
            HttpServletRequest httpServletRequest
    ) {
        requireLegacySessionMode();
        AuthenticatedUser user = authUseCase.register(request == null
                ? null
                : new RegisterUserCommand(request.email(), request.password()));
        currentUserService.establishCurrentUser(httpServletRequest.getSession(true), user.id().toString());
        return toResponse(user);
    }

    @PostMapping("/api/auth/login")
    public AuthenticatedUserResponse login(
            @RequestBody(required = false) LoginRequest request,
            HttpServletRequest httpServletRequest
    ) {
        requireLegacySessionMode();
        AuthenticatedUser user = authUseCase.login(request == null
                ? null
                : new LoginUserCommand(request.email(), request.password()));
        currentUserService.establishCurrentUser(httpServletRequest.getSession(true), user.id().toString());
        return toResponse(user);
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpServletRequest) {
        requireLegacySessionMode();
        HttpSession session = httpServletRequest.getSession(false);
        if (session != null) {
            currentUserService.clearCurrentUser(session);
        }
    }

    @GetMapping("/api/me")
    public AuthenticatedUserResponse getCurrentUser() {
        String authenticatedUserId = securityProperties.isKeycloakJwtMode()
                ? currentUserService.getCurrentUserId()
                : currentUserService.getAuthenticatedSessionUserId();
        return toResponse(authUseCase.getUser(authenticatedUserId));
    }

    private void requireLegacySessionMode() {
        if (!securityProperties.isLegacySessionMode()) {
            throw new AuthModeUnavailableException("Legacy session authentication is unavailable in keycloak_jwt mode");
        }
    }

    private AuthenticatedUserResponse toResponse(AuthenticatedUser user) {
        return new AuthenticatedUserResponse(user.id(), user.email());
    }
}
