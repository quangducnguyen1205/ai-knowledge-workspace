package com.aiknowledgeworkspace.workspacecore.common.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class CurrentUserServiceTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsHeaderUserIdWhenPresent() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(properties.getHeaderName(), "  study-user-2  ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(currentUserService.getCurrentUserId()).isEqualTo("study-user-2");
    }

    @Test
    void returnsDefaultUserIdWhenHeaderIsMissing() {
        CurrentUserProperties properties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(properties);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThat(currentUserService.getCurrentUserId()).isEqualTo(properties.getDefaultId());
    }
}
