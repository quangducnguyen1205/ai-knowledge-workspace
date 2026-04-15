package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserProperties;
import com.aiknowledgeworkspace.workspacecore.common.identity.CurrentUserService;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceProperties;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceRepository;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    private MockRestServiceServer mockServer;
    private CurrentUserService currentUserService;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:9201");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        CurrentUserProperties currentUserProperties = new CurrentUserProperties();
        currentUserService = new CurrentUserService(currentUserProperties);
        WorkspaceService workspaceService = new WorkspaceService(
                workspaceRepository,
                new WorkspaceProperties(),
                currentUserService
        );

        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl("http://localhost:9201");
        properties.setTranscriptIndexName("asset-transcript-rows");

        searchService = new SearchService(builder.build(), properties, workspaceService);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void searchUsesCurrentUserFromSessionAuthEntryForDefaultWorkspaceScope() {
        String currentUserId = "session-user";
        Workspace defaultWorkspace = new Workspace(java.util.UUID.randomUUID(), "Default Workspace", currentUserId, true);

        bindSessionCurrentUser(currentUserId);
        org.mockito.Mockito.when(workspaceRepository.findByOwnerIdAndDefaultWorkspaceTrue(currentUserId))
                .thenReturn(Optional.of(defaultWorkspace));
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"workspaceId.keyword\":\"" + defaultWorkspace.getId() + "\"")))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));

        SearchResponse response = searchService.search("dynamic programming", null, null);

        assertThat(response.workspaceIdFilter()).isEqualTo(defaultWorkspace.getId());
        assertThat(response.resultCount()).isZero();
        mockServer.verify();
    }

    private void bindSessionCurrentUser(String currentUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        currentUserService.establishCurrentUser(session, currentUserId);
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
