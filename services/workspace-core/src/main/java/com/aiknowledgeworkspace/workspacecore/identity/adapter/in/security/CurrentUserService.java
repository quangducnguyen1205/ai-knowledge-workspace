package com.aiknowledgeworkspace.workspacecore.identity.adapter.in.security;

import com.aiknowledgeworkspace.workspacecore.identity.api.CurrentUserContext;
import com.aiknowledgeworkspace.workspacecore.identity.application.configuration.CurrentUserProperties;
import com.aiknowledgeworkspace.workspacecore.identity.application.configuration.WorkspaceSecurityProperties;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.AuthenticationRequiredException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.InvalidCurrentUserIdException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.InvalidJwtIdentityException;
import com.aiknowledgeworkspace.workspacecore.identity.application.service.OidcUserProvisioningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class CurrentUserService implements CurrentUserContext {

    private static final int MAX_CURRENT_USER_ID_LENGTH = 255;

    private final CurrentUserProperties currentUserProperties;
    private final WorkspaceSecurityProperties securityProperties;
    private final OidcUserProvisioningService oidcUserProvisioningService;
    private final OidcJwtIdentityMapper oidcJwtIdentityMapper;

    public CurrentUserService(CurrentUserProperties currentUserProperties) {
        this(currentUserProperties, new WorkspaceSecurityProperties(), null, null);
    }

    @Autowired
    public CurrentUserService(
            CurrentUserProperties currentUserProperties,
            WorkspaceSecurityProperties securityProperties,
            OidcUserProvisioningService oidcUserProvisioningService,
            OidcJwtIdentityMapper oidcJwtIdentityMapper
    ) {
        this.currentUserProperties = currentUserProperties;
        this.securityProperties = securityProperties;
        this.oidcUserProvisioningService = oidcUserProvisioningService;
        this.oidcJwtIdentityMapper = oidcJwtIdentityMapper;
    }

    public String getCurrentUserId() {
        if (securityProperties.isKeycloakJwtMode()) {
            return resolveJwtProductUserId();
        }

        ServletRequestAttributes requestAttributes = getServletRequestAttributes();
        if (requestAttributes == null) {
            throw new AuthenticationRequiredException("Authentication is required");
        }

        String sessionUserId = resolveSessionUserId(requestAttributes.getRequest());
        if (StringUtils.hasText(sessionUserId)) {
            return sessionUserId;
        }

        throw new AuthenticationRequiredException("Authentication is required");
    }

    public String getAuthenticatedSessionUserId() {
        ServletRequestAttributes requestAttributes = getServletRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }

        return resolveSessionUserId(requestAttributes.getRequest());
    }

    public String establishCurrentUser(HttpSession session, String requestedUserId) {
        String normalizedUserId = normalizeRequestedUserId(requestedUserId);
        session.setAttribute(currentUserProperties.getSessionAttributeName(), normalizedUserId);
        return normalizedUserId;
    }

    public void clearCurrentUser(HttpSession session) {
        session.removeAttribute(currentUserProperties.getSessionAttributeName());
        session.invalidate();
    }

    private ServletRequestAttributes getServletRequestAttributes() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes;
        }

        return null;
    }

    private String resolveJwtProductUserId() {
        if (oidcUserProvisioningService == null || oidcJwtIdentityMapper == null) {
            throw new AuthenticationRequiredException("Authentication is required");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)
                || !authentication.isAuthenticated()) {
            throw new AuthenticationRequiredException("Authentication is required");
        }

        Jwt jwt = jwtAuthenticationToken.getToken();
        try {
            return oidcUserProvisioningService.resolveUser(oidcJwtIdentityMapper.toIdentity(jwt)).getId().toString();
        } catch (InvalidJwtIdentityException exception) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
    }

    private String resolveSessionUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        Object sessionValue = session.getAttribute(currentUserProperties.getSessionAttributeName());
        if (sessionValue instanceof String sessionUserId && StringUtils.hasText(sessionUserId)) {
            return sessionUserId.trim();
        }

        return null;
    }

    private String normalizeRequestedUserId(String requestedUserId) {
        if (!StringUtils.hasText(requestedUserId)) {
            throw new InvalidCurrentUserIdException("userId is required");
        }

        String normalizedUserId = requestedUserId.trim();
        if (normalizedUserId.length() > MAX_CURRENT_USER_ID_LENGTH) {
            throw new InvalidCurrentUserIdException(
                    "userId must be at most " + MAX_CURRENT_USER_ID_LENGTH + " characters"
            );
        }

        return normalizedUserId;
    }
}
