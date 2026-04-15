package com.aiknowledgeworkspace.workspacecore.common.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUserService currentUserService;

    public AuthController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @PostMapping("/session")
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
}
