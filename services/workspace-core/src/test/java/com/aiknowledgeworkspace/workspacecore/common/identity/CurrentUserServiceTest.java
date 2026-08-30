package com.aiknowledgeworkspace.workspacecore.identity.adapter.in.security;

import com.aiknowledgeworkspace.workspacecore.identity.application.configuration.CurrentUserProperties;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.AuthenticationRequiredException;
import com.aiknowledgeworkspace.workspacecore.identity.application.exception.InvalidCurrentUserIdException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

class CurrentUserServiceTest {

    private static final String FORGED_IDENTITY_HEADER = "X-Current-User-Id";

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsSessionUserIdWhenPresent() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(properties.getSessionAttributeName(), "  study-user-1  ");
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(currentUserService.getCurrentUserId()).isEqualTo("study-user-1");
    }

    @Test
    void returnsSessionUserIdAndIgnoresIdentityHeader() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(properties.getSessionAttributeName(), "session-user");
        request.setSession(session);
        request.addHeader(FORGED_IDENTITY_HEADER, "header-user");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(currentUserService.getCurrentUserId()).isEqualTo("session-user");
    }

    @Test
    void rejectsRequestCarryingOnlyAnIdentityHeader() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(FORGED_IDENTITY_HEADER, "header-user");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThatThrownBy(currentUserService::getCurrentUserId)
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Authentication is required");
    }

    @Test
    void rejectsAnonymousRequest() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThatThrownBy(currentUserService::getCurrentUserId)
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Authentication is required");
    }

    @Test
    void rejectsNonServletRequestContext() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        RequestContextHolder.setRequestAttributes(new NonServletRequestAttributes());

        assertThatThrownBy(currentUserService::getCurrentUserId)
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessage("Authentication is required");
        assertThat(currentUserService.getAuthenticatedSessionUserId()).isNull();
    }

    @Test
    void getAuthenticatedSessionUserIdReturnsOnlySessionUser() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(FORGED_IDENTITY_HEADER, "header-user");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(currentUserService.getAuthenticatedSessionUserId()).isNull();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(properties.getSessionAttributeName(), "session-user");
        request.setSession(session);

        assertThat(currentUserService.getAuthenticatedSessionUserId()).isEqualTo("session-user");
    }

    @Test
    void establishCurrentUserStoresTrimmedUserIdInSession() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);
        MockHttpSession session = new MockHttpSession();

        String currentUserId = currentUserService.establishCurrentUser(session, "  study-user-3  ");

        assertThat(currentUserId).isEqualTo("study-user-3");
        assertThat(session.getAttribute(properties.getSessionAttributeName())).isEqualTo("study-user-3");
    }

    @Test
    void establishCurrentUserRejectsBlankUserId() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        assertThatThrownBy(() -> currentUserService.establishCurrentUser(new MockHttpSession(), "   "))
                .isInstanceOf(InvalidCurrentUserIdException.class)
                .hasMessage("userId is required");
    }

    @Test
    void clearCurrentUserInvalidatesSession() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(properties.getSessionAttributeName(), "study-user-4");

        currentUserService.clearCurrentUser(session);

        assertThat(session.isInvalid()).isTrue();
    }

    private static final class NonServletRequestAttributes implements RequestAttributes {

        @Override
        public Object getAttribute(String name, int scope) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
        }

        @Override
        public void removeAttribute(String name, int scope) {
        }

        @Override
        public String[] getAttributeNames(int scope) {
            return new String[0];
        }

        @Override
        public void registerDestructionCallback(String name, Runnable callback, int scope) {
        }

        @Override
        public Object resolveReference(String key) {
            return null;
        }

        @Override
        public String getSessionId() {
            return "non-servlet-session";
        }

        @Override
        public Object getSessionMutex() {
            return this;
        }
    }
}
