package com.aiknowledgeworkspace.workspacecore.common.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class CurrentUserService {

    private static final int MAX_CURRENT_USER_ID_LENGTH = 255;

    private final CurrentUserProperties currentUserProperties;

    public CurrentUserService(CurrentUserProperties currentUserProperties) {
        this.currentUserProperties = currentUserProperties;
    }

    public String getCurrentUserId() {
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return currentUserProperties.getDefaultId();
        }

        HttpServletRequest request = requestAttributes.getRequest();
        String sessionUserId = resolveSessionUserId(request);
        if (StringUtils.hasText(sessionUserId)) {
            return sessionUserId;
        }

        String requestUserId = request.getHeader(currentUserProperties.getHeaderName());
        if (StringUtils.hasText(requestUserId)) {
            return requestUserId.trim();
        }

        return currentUserProperties.getDefaultId();
    }

    public String getAuthenticatedSessionUserId() {
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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

    public boolean isDefaultUser(String userId) {
        return currentUserProperties.getDefaultId().equals(userId);
    }

    public String getHeaderName() {
        return currentUserProperties.getHeaderName();
    }

    public String getSessionAttributeName() {
        return currentUserProperties.getSessionAttributeName();
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
