package com.aiknowledgeworkspace.workspacecore.common.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private CurrentUserProperties currentUserProperties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        currentUserProperties = new CurrentUserProperties();
        CurrentUserService currentUserService = new CurrentUserService(currentUserProperties);
        AuthController authController = new AuthController(currentUserService);

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createSessionStoresCurrentUserInHttpSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "  study-user-1  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("study-user-1"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(currentUserProperties.getSessionAttributeName()))
                .isEqualTo("study-user-1");
    }

    @Test
    void createSessionRejectsBlankUserId() throws Exception {
        mockMvc.perform(post("/api/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_USER_ID"))
                .andExpect(jsonPath("$.message").value("userId is required"));
    }

    @Test
    void createSessionRejectsMissingBody() throws Exception {
        mockMvc.perform(post("/api/auth/session")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_USER_ID"))
                .andExpect(jsonPath("$.message").value("userId is required"));
    }
}
