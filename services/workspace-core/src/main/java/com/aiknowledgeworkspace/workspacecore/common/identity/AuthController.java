package com.aiknowledgeworkspace.workspacecore.common.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/auth/session")
    public AuthSessionResponse createSession(
            @RequestBody(required = false) CreateAuthSessionRequest request,
            HttpServletRequest httpServletRequest
    ) {
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
        return authService.register(request, httpServletRequest.getSession(true));
    }

    @PostMapping("/api/auth/login")
    public AuthenticatedUserResponse login(
            @RequestBody(required = false) LoginRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return authService.login(request, httpServletRequest.getSession(true));
    }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpServletRequest) {
        authService.logout(httpServletRequest.getSession(false));
    }

    @GetMapping("/api/me")
    public AuthenticatedUserResponse getCurrentUser() {
        return authService.getCurrentUser();
    }
}
