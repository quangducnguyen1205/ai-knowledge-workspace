package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.adapter.in.web.SearchApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.search.adapter.in.web.SearchController;
import com.aiknowledgeworkspace.workspacecore.search.application.service.SearchApplicationService;
import com.aiknowledgeworkspace.workspacecore.search.adapter.out.search.ElasticsearchTranscriptAdapter;

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

import com.aiknowledgeworkspace.workspacecore.asset.adapter.in.web.AssetApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.search.adapter.out.search.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.common.web.adapter.in.web.ApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetDetails;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetQueryPort;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchAssetUnavailableException;
import com.aiknowledgeworkspace.workspacecore.workspace.application.exception.WorkspaceNotFoundException;
import com.aiknowledgeworkspace.workspacecore.workspace.adapter.in.web.WorkspaceApiExceptionHandler;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
    private WorkspaceAccessUseCase workspaceQueryApplication;
    private SearchAssetQueryPort searchAssetQueryPort;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:9201");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        workspaceQueryApplication = mock(WorkspaceAccessUseCase.class);
        searchAssetQueryPort = mock(SearchAssetQueryPort.class);

        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl("http://localhost:9201");
        properties.setTranscriptIndexName("asset-transcript-rows");

        ElasticsearchTranscriptAdapter searchIndexClient = new ElasticsearchTranscriptAdapter(
                builder.build(),
                properties,
                new ObjectMapper()
        );
        SearchApplicationService searchService = new SearchApplicationService(workspaceQueryApplication, searchAssetQueryPort, searchIndexClient);
        SearchController searchController = new SearchController(searchService);

        mockMvc = MockMvcBuilders.standaloneSetup(searchController)
                .setControllerAdvice(
                        new ApiExceptionHandler(),
                        new SearchApiExceptionHandler(),
                        new AssetApiExceptionHandler(),
                        new WorkspaceApiExceptionHandler()
                )
                .build();
    }

    @Test
    void searchReturnsTranscriptRowResultsFromSpring() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(workspaceQueryApplication.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(searchAssetQueryPort.getAuthorizedAssetDetails(assetId))
                .thenReturn(new SearchAssetDetails(assetId, workspaceId, true));
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
                          "startMs": 1234,
                          "endMs": 5678,
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
                .andExpect(content().string(containsString("\"multi_match\":{\"query\":\"dynamic programming\",\"fields\":[\"text^4\",\"assetTitle^2\"],\"type\":\"cross_fields\",\"minimum_should_match\":\"2<75%\"}")))
                .andExpect(content().string(containsString("\"match_phrase\":{\"text\":{\"query\":\"dynamic programming\",\"boost\":10.0,\"slop\":0}}")))
                .andExpect(content().string(containsString("\"match_phrase\":{\"text\":{\"query\":\"dynamic programming\",\"boost\":6.0,\"slop\":2}}")))
                .andExpect(content().string(containsString("\"match_phrase\":{\"assetTitle\":{\"query\":\"dynamic programming\",\"boost\":8.0,\"slop\":0}}")))
                .andExpect(content().string(containsString("\"size\":60")))
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
                .andExpect(jsonPath("$.results[0].startMs").value(1234))
                .andExpect(jsonPath("$.results[0].endMs").value(5678))
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
        when(workspaceQueryApplication.resolveWorkspaceId(null)).thenReturn(defaultWorkspaceId);
        when(searchAssetQueryPort.findSearchableAssetIdsInWorkspace(defaultWorkspaceId))
                .thenReturn(List.of(UUID.randomUUID()));

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"workspaceId.keyword\":\"" + defaultWorkspaceId + "\"")))
                .andExpect(content().string(containsString("\"terms\":{\"assetId.keyword\"")))
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
        UUID searchableAssetId = UUID.randomUUID();
        when(workspaceQueryApplication.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(searchAssetQueryPort.findSearchableAssetIdsInWorkspace(workspaceId))
                .thenReturn(List.of(searchableAssetId));

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"workspaceId.keyword\":\"" + workspaceId + "\"")))
                .andExpect(content().string(containsString("\"assetStatus.keyword\":\"SEARCHABLE\"")))
                .andExpect(content().string(containsString("\"terms\":{\"assetId.keyword\":[\"" + searchableAssetId + "\"]")))
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
    void searchWithAssetFromDifferentWorkspaceReturnsOwnershipSafeNotFound() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID otherWorkspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaceQueryApplication.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(searchAssetQueryPort.getAuthorizedAssetDetails(assetId))
                .thenReturn(new SearchAssetDetails(assetId, otherWorkspaceId, true));

        mockMvc.perform(get("/api/search")
                        .param("q", "consensus")
                        .param("workspaceId", workspaceId.toString())
                        .param("assetId", assetId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Asset not found"));

        mockServer.verify();
    }

    @Test
    void searchWithUnknownOrNonOwnedAssetReturnsOwnershipSafeNotFound() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(workspaceQueryApplication.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(searchAssetQueryPort.getAuthorizedAssetDetails(assetId))
                .thenThrow(new SearchAssetUnavailableException(new IllegalStateException("unavailable")));

        mockMvc.perform(get("/api/search")
                        .param("q", "consensus")
                        .param("workspaceId", workspaceId.toString())
                        .param("assetId", assetId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Asset not found"));

        mockServer.verify();
    }

    @Test
    void searchAddsPhraseBoostLayerWithoutChangingResponseContract() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID searchableAssetId = UUID.randomUUID();
        when(workspaceQueryApplication.resolveWorkspaceId(workspaceId)).thenReturn(workspaceId);
        when(searchAssetQueryPort.findSearchableAssetIdsInWorkspace(workspaceId))
                .thenReturn(List.of(searchableAssetId));

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"multi_match\":{\"query\":\"binary search tree\",\"fields\":[\"text^4\",\"assetTitle^2\"],\"type\":\"cross_fields\",\"minimum_should_match\":\"2<75%\"}")))
                .andExpect(content().string(containsString("\"terms\":{\"assetId.keyword\":[\"" + searchableAssetId + "\"]")))
                .andExpect(content().string(containsString("\"should\":[")))
                .andExpect(content().string(containsString("\"match_phrase\":{\"text\":{\"query\":\"binary search tree\",\"boost\":10.0,\"slop\":0}}")))
                .andExpect(content().string(containsString("\"match_phrase\":{\"text\":{\"query\":\"binary search tree\",\"boost\":6.0,\"slop\":2}}")))
                .andExpect(content().string(containsString("\"match_phrase\":{\"assetTitle\":{\"query\":\"binary search tree\",\"boost\":8.0,\"slop\":0}}")))
                .andExpect(content().string(containsString("\"match_phrase\":{\"assetTitle\":{\"query\":\"binary search tree\",\"boost\":4.0,\"slop\":2}}")))
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
    void invalidAssetIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "dynamic programming")
                        .param("assetId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Invalid value for request parameter assetId"));
    }

    @Test
    void unknownOrNonOwnedWorkspaceReturnsNotFound() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(workspaceQueryApplication.resolveWorkspaceId(workspaceId))
                .thenThrow(new WorkspaceNotFoundException(workspaceId));

        mockMvc.perform(get("/api/search")
                        .param("q", "dynamic programming")
                        .param("workspaceId", workspaceId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Workspace not found"));
    }
}
