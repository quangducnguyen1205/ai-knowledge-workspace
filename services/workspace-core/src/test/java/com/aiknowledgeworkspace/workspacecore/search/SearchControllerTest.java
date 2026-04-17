package com.aiknowledgeworkspace.workspacecore.search;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.common.web.ApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceNotFoundException;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

class SearchControllerTest {

    private MockRestServiceServer mockServer;
    private MockMvc mockMvc;
    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:9201");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        workspaceService = mock(WorkspaceService.class);

        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl("http://localhost:9201");
        properties.setTranscriptIndexName("asset-transcript-rows");

        SearchService searchService = new SearchService(builder.build(), properties, workspaceService);
        SearchController searchController = new SearchController(searchService);

        mockMvc = MockMvcBuilders.standaloneSetup(searchController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void searchReturnsTranscriptRowResultsFromSpring() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(workspaceService.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new Workspace(workspaceId, "Algorithms"));
        String searchResponse = """
                {
                  "hits": {
                    "hits": [
                      {
                        "_score": 2.75,
                        "_source": {
                          "assetId": "%s",
                          "workspaceId": "%s",
                          "assetTitle": "Lecture 5",
                          "transcriptRowId": "row-55",
                          "segmentIndex": 4,
                          "text": "Dynamic programming solves overlapping subproblems.",
                          "createdAt": "2026-03-26T00:00:00Z",
                          "assetStatus": "SEARCHABLE"
                        }
                      }
                    ]
                  }
                }
                """.formatted(assetId, workspaceId);

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"query\":\"dynamic programming\"")))
                .andExpect(content().string(containsString("\"workspaceId.keyword\":\"" + workspaceId + "\"")))
                .andExpect(content().string(containsString("\"assetStatus.keyword\":\"SEARCHABLE\"")))
                .andExpect(content().string(containsString("\"assetId.keyword\":\"" + assetId + "\"")))
                .andExpect(content().string(containsString("\"match_phrase\":{\"text\":{\"query\":\"dynamic programming\",\"boost\":6.0}}")))
                .andExpect(content().string(containsString("\"match_phrase\":{\"assetTitle\":{\"query\":\"dynamic programming\",\"boost\":4.0}}")))
                .andExpect(content().string(containsString("\"_score\":{\"order\":\"desc\"}")))
                .andExpect(content().string(containsString("\"segmentIndex\":{\"order\":\"asc\"}")))
                .andExpect(content().string(containsString("\"assetId.keyword\":{\"order\":\"asc\"}")))
                .andExpect(content().string(containsString("\"transcriptRowId.keyword\":{\"order\":\"asc\",\"missing\":\"_last\"}")))
                .andRespond(withSuccess(searchResponse, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/search")
                        .param("q", "dynamic programming")
                        .param("workspaceId", workspaceId.toString())
                        .param("assetId", assetId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("dynamic programming"))
                .andExpect(jsonPath("$.workspaceIdFilter").value(workspaceId.toString()))
                .andExpect(jsonPath("$.assetIdFilter").value(assetId.toString()))
                .andExpect(jsonPath("$.resultCount").value(1))
                .andExpect(jsonPath("$.results[0].assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.results[0].assetTitle").value("Lecture 5"))
                .andExpect(jsonPath("$.results[0].transcriptRowId").value("row-55"))
                .andExpect(jsonPath("$.results[0].segmentIndex").value(4))
                .andExpect(jsonPath("$.results[0].text")
                        .value("Dynamic programming solves overlapping subproblems."))
                .andExpect(jsonPath("$.results[0].createdAt").value("2026-03-26T00:00:00Z"))
                .andExpect(jsonPath("$.results[0].score").value(2.75));

        mockServer.verify();
    }

    @Test
    void blankQueryReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"))
                .andExpect(jsonPath("$.message").value("q query parameter is required"));
    }

    @Test
    void missingQueryReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"))
                .andExpect(jsonPath("$.message").value("q query parameter is required"));
    }

    @Test
    void searchUsesDefaultWorkspaceWhenWorkspaceIdIsOmitted() throws Exception {
        UUID defaultWorkspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(workspaceService.resolveWorkspaceOrDefault(null))
                .thenReturn(new Workspace(defaultWorkspaceId, "Default Workspace"));

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"workspaceId.keyword\":\"" + defaultWorkspaceId + "\"")))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/search")
                        .param("q", "binary tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceIdFilter").value(defaultWorkspaceId.toString()))
                .andExpect(jsonPath("$.resultCount").value(0));

        mockServer.verify();
    }

    @Test
    void searchWithoutAssetIdStillFiltersByResolvedWorkspaceAndSearchableAssets() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceService.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new Workspace(workspaceId, "Systems"));

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"workspaceId.keyword\":\"" + workspaceId + "\"")))
                .andExpect(content().string(containsString("\"assetStatus.keyword\":\"SEARCHABLE\"")))
                .andExpect(content().string(not(containsString("\"term\":{\"assetId.keyword\""))))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/search")
                        .param("q", "operating systems")
                        .param("workspaceId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceIdFilter").value(workspaceId.toString()))
                .andExpect(jsonPath("$.assetIdFilter").value(nullValue()))
                .andExpect(jsonPath("$.resultCount").value(0));

        mockServer.verify();
    }

    @Test
    void searchAddsPhraseBoostLayerWithoutChangingResponseContract() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceService.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new Workspace(workspaceId, "Systems"));

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"multi_match\":{\"query\":\"binary search tree\",\"fields\":[\"text^3\",\"assetTitle\"]}")))
                .andExpect(content().string(containsString("\"should\":[")))
                .andExpect(content().string(containsString("\"match_phrase\":{\"text\":{\"query\":\"binary search tree\",\"boost\":6.0}}")))
                .andExpect(content().string(containsString("\"match_phrase\":{\"assetTitle\":{\"query\":\"binary search tree\",\"boost\":4.0}}")))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/search")
                        .param("q", "binary search tree")
                        .param("workspaceId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("binary search tree"))
                .andExpect(jsonPath("$.workspaceIdFilter").value(workspaceId.toString()))
                .andExpect(jsonPath("$.assetIdFilter").value(nullValue()))
                .andExpect(jsonPath("$.resultCount").value(0))
                .andExpect(jsonPath("$.results").isArray());

        mockServer.verify();
    }

    @Test
    void invalidWorkspaceIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "dynamic programming")
                        .param("workspaceId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"))
                .andExpect(jsonPath("$.message").value("workspaceId must be a valid UUID"));
    }

    @Test
    void unknownOrNonOwnedWorkspaceReturnsNotFound() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceService.resolveWorkspaceOrDefault(workspaceId))
                .thenThrow(new WorkspaceNotFoundException(workspaceId));

        mockMvc.perform(get("/api/search")
                        .param("q", "dynamic programming")
                        .param("workspaceId", workspaceId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Workspace not found: " + workspaceId));
    }
}
