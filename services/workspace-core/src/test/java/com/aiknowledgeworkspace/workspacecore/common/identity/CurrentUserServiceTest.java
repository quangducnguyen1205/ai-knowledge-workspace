package com.aiknowledgeworkspace.workspacecore.common.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class CurrentUserServiceTest {

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
    void returnsSessionUserIdBeforeHeaderFallback() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(properties.getSessionAttributeName(), "session-user");
        request.setSession(session);
        request.addHeader(properties.getHeaderName(), "header-user");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(currentUserService.getCurrentUserId()).isEqualTo("session-user");
    }

    @Test
    void returnsHeaderUserIdWhenSessionIsMissing() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(properties.getHeaderName(), "  study-user-2  ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(currentUserService.getCurrentUserId()).isEqualTo("study-user-2");
    }

    @Test
    void returnsDefaultUserIdWhenSessionAndHeaderAreMissing() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThat(currentUserService.getCurrentUserId()).isEqualTo(properties.getDefaultId());
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
}
