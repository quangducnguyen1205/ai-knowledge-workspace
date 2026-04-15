package com.aiknowledgeworkspace.workspacecore.asset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiknowledgeworkspace.workspacecore.common.web.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchConnectivityException;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchIntegrationException;
import com.aiknowledgeworkspace.workspacecore.search.TranscriptIndexingService;
import com.aiknowledgeworkspace.workspacecore.workspace.WorkspaceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssetControllerTest {

    private AssetService assetService;
    private AssetDeletionService assetDeletionService;
    private AssetTitleUpdateService assetTitleUpdateService;
    private TranscriptIndexingService transcriptIndexingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        assetService = mock(AssetService.class);
        assetDeletionService = mock(AssetDeletionService.class);
        assetTitleUpdateService = mock(AssetTitleUpdateService.class);
        transcriptIndexingService = mock(TranscriptIndexingService.class);
        AssetController assetController = new AssetController(
                assetService,
                assetDeletionService,
                assetTitleUpdateService,
                transcriptIndexingService
        );
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(assetController)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void uploadReturnsStructuredNotFoundForUnknownOrNonOwnedWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture.mp4",
                "video/mp4",
                "video-bytes".getBytes()
        );
        when(assetService.uploadAsset(workspaceId, file, "Lecture 1"))
                .thenThrow(new WorkspaceNotFoundException(workspaceId));

        mockMvc.perform(multipart("/api/assets/upload")
                        .file(file)
                        .param("workspaceId", workspaceId.toString())
                        .param("title", "Lecture 1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Workspace not found: " + workspaceId));
    }

    @Test
    void listAssetsReturnsPaginatedWorkspaceScopedAssetSummaries() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(assetService.listAssets(workspaceId, null, null, null)).thenReturn(new AssetListResponse(
                List.of(new AssetSummaryResponse(
                        assetId,
                        "Lecture 1",
                        AssetStatus.SEARCHABLE,
                        workspaceId,
                        Instant.parse("2026-04-10T03:00:00Z")
                )),
                0,
                20,
                1,
                1,
                false
        ));

        mockMvc.perform(get("/api/assets")
                        .param("workspaceId", workspaceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items[0].assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.items[0].title").value("Lecture 1"))
                .andExpect(jsonPath("$.items[0].assetStatus").value("SEARCHABLE"))
                .andExpect(jsonPath("$.items[0].workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-04-10T03:00:00Z"));
    }

    @Test
    void listAssetsSupportsExplicitPageSizeAndAssetStatusFilter() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(assetService.listAssets(workspaceId, 1, 10, AssetStatus.SEARCHABLE)).thenReturn(new AssetListResponse(
                List.of(),
                1,
                10,
                25,
                3,
                true
        ));

        mockMvc.perform(get("/api/assets")
                        .param("workspaceId", workspaceId.toString())
                        .param("page", "1")
                        .param("size", "10")
                        .param("assetStatus", "SEARCHABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void listAssetsReturnsStructuredBadRequestForMalformedWorkspaceId() throws Exception {
        mockMvc.perform(get("/api/assets")
                        .param("workspaceId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WORKSPACE_ID"))
                .andExpect(jsonPath("$.message").value("workspaceId must be a valid UUID"));
    }

    @Test
    void listAssetsReturnsStructuredNotFoundForUnknownOrNonOwnedWorkspace() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(assetService.listAssets(workspaceId, null, null, null))
                .thenThrow(new WorkspaceNotFoundException(workspaceId));

        mockMvc.perform(get("/api/assets")
                        .param("workspaceId", workspaceId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Workspace not found: " + workspaceId));
    }

    @Test
    void listAssetsReturnsStructuredBadRequestForInvalidPage() throws Exception {
        when(assetService.listAssets(null, -1, null, null))
                .thenThrow(new AssetListRequestException(
                        "INVALID_ASSET_PAGE",
                        "page must be greater than or equal to 0"
                ));

        mockMvc.perform(get("/api/assets")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ASSET_PAGE"))
                .andExpect(jsonPath("$.message").value("page must be greater than or equal to 0"));
    }

    @Test
    void listAssetsReturnsStructuredBadRequestForInvalidSize() throws Exception {
        when(assetService.listAssets(null, null, 0, null))
                .thenThrow(new AssetListRequestException(
                        "INVALID_ASSET_SIZE",
                        "size must be greater than 0"
                ));

        mockMvc.perform(get("/api/assets")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ASSET_SIZE"))
                .andExpect(jsonPath("$.message").value("size must be greater than 0"));
    }

    @Test
    void listAssetsReturnsStructuredBadRequestForInvalidAssetStatus() throws Exception {
        mockMvc.perform(get("/api/assets")
                        .param("assetStatus", "NOT_A_REAL_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ASSET_STATUS"))
                .andExpect(jsonPath("$.message").value(
                        "assetStatus must be one of: PROCESSING, TRANSCRIPT_READY, SEARCHABLE, FAILED"
                ));
    }

    @Test
    void getAssetReturnsOwnedAsset() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset asset = new Asset(
                "lecture.mp4",
                "Lecture 1",
                AssetStatus.SEARCHABLE,
                new com.aiknowledgeworkspace.workspacecore.workspace.Workspace(workspaceId, "Algorithms")
        );
        org.springframework.test.util.ReflectionTestUtils.setField(asset, "id", assetId);
        org.springframework.test.util.ReflectionTestUtils.setField(asset, "createdAt", Instant.parse("2026-04-10T03:00:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(asset, "updatedAt", Instant.parse("2026-04-10T03:05:00Z"));
        when(assetService.getAsset(assetId)).thenReturn(asset);

        mockMvc.perform(get("/api/assets/{assetId}", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assetId.toString()))
                .andExpect(jsonPath("$.title").value("Lecture 1"))
                .andExpect(jsonPath("$.status").value("SEARCHABLE"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()));
    }

    @Test
    void getAssetReturnsNotFoundWhenAssetIsNotOwned() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAsset(assetId)).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Asset not found"
        ));

        mockMvc.perform(get("/api/assets/{assetId}", assetId))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Asset not found"));
    }

    @Test
    void getAssetStatusReturnsOwnedAssetStatus() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID processingJobId = UUID.randomUUID();
        when(assetService.getAssetStatus(assetId)).thenReturn(new AssetStatusResponse(
                assetId,
                processingJobId,
                AssetStatus.TRANSCRIPT_READY,
                com.aiknowledgeworkspace.workspacecore.processing.ProcessingJobStatus.SUCCEEDED
        ));

        mockMvc.perform(get("/api/assets/{assetId}/status", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.processingJobId").value(processingJobId.toString()))
                .andExpect(jsonPath("$.assetStatus").value("TRANSCRIPT_READY"))
                .andExpect(jsonPath("$.processingJobStatus").value("SUCCEEDED"));
    }

    @Test
    void getAssetStatusReturnsNotFoundWhenAssetIsNotOwned() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAssetStatus(assetId)).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Asset not found"
        ));

        mockMvc.perform(get("/api/assets/{assetId}/status", assetId))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Asset not found"));
    }

    @Test
    void getAssetTranscriptReturnsOwnedTranscript() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAssetTranscript(assetId)).thenReturn(List.of(
                new AssetTranscriptRowResponse(
                        "row-1",
                        "video-1",
                        1,
                        "Transcript row",
                        "2026-04-11T00:00:00Z"
                )
        ));

        mockMvc.perform(get("/api/assets/{assetId}/transcript", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("row-1"))
                .andExpect(jsonPath("$[0].videoId").value("video-1"))
                .andExpect(jsonPath("$[0].segmentIndex").value(1))
                .andExpect(jsonPath("$[0].text").value("Transcript row"));
    }

    @Test
    void getAssetTranscriptReturnsNotFoundWhenAssetIsNotOwned() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAssetTranscript(assetId)).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Asset not found"
        ));

        mockMvc.perform(get("/api/assets/{assetId}/transcript", assetId))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Asset not found"));
    }

    @Test
    void deleteAssetReturnsNoContent() throws Exception {
        UUID assetId = UUID.randomUUID();

        mockMvc.perform(delete("/api/assets/{assetId}", assetId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAssetReturnsNotFoundWhenAssetDoesNotExist() throws Exception {
        UUID assetId = UUID.randomUUID();
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Asset not found"
        )).when(assetDeletionService).deleteAsset(assetId);

        mockMvc.perform(delete("/api/assets/{assetId}", assetId))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Asset not found"));
    }

    @Test
    void deleteAssetReturnsNotFoundWhenAssetIsNotOwned() throws Exception {
        UUID assetId = UUID.randomUUID();
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Asset not found"
        )).when(assetDeletionService).deleteAsset(assetId);

        mockMvc.perform(delete("/api/assets/{assetId}", assetId))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Asset not found"));
    }

    @Test
    void deleteAssetReturnsStructuredServiceUnavailableWhenElasticsearchIsUnavailable() throws Exception {
        UUID assetId = UUID.randomUUID();
        doThrow(new ElasticsearchConnectivityException(
                "Elasticsearch is unavailable while trying to delete transcript documents for asset " + assetId,
                new RuntimeException("connection refused")
        )).when(assetDeletionService).deleteAsset(assetId);

        mockMvc.perform(delete("/api/assets/{assetId}", assetId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ELASTICSEARCH_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        "Elasticsearch is unavailable while trying to delete transcript documents for asset " + assetId
                ));
    }

    @Test
    void deleteAssetReturnsStructuredBadGatewayWhenElasticsearchReturnsIntegrationError() throws Exception {
        UUID assetId = UUID.randomUUID();
        doThrow(new ElasticsearchIntegrationException(
                "Elasticsearch returned HTTP 500 while trying to delete transcript documents for asset " + assetId
        )).when(assetDeletionService).deleteAsset(assetId);

        mockMvc.perform(delete("/api/assets/{assetId}", assetId))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ELASTICSEARCH_INTEGRATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Elasticsearch returned HTTP 500 while trying to delete transcript documents for asset " + assetId
                ));
    }

    @Test
    void updateAssetTitleReturnsUpdatedAsset() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Asset updatedAsset = new Asset("lecture.mp4", "New Title", AssetStatus.SEARCHABLE, new com.aiknowledgeworkspace.workspacecore.workspace.Workspace(workspaceId, "Algorithms"));
        org.springframework.test.util.ReflectionTestUtils.setField(updatedAsset, "id", assetId);
        org.springframework.test.util.ReflectionTestUtils.setField(updatedAsset, "createdAt", Instant.parse("2026-04-10T03:00:00Z"));
        org.springframework.test.util.ReflectionTestUtils.setField(updatedAsset, "updatedAt", Instant.parse("2026-04-10T03:05:00Z"));

        when(assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("New Title")))
                .thenReturn(updatedAsset);

        mockMvc.perform(patch("/api/assets/{assetId}", assetId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New Title"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assetId.toString()))
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.status").value("SEARCHABLE"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()));
    }

    @Test
    void updateAssetTitleReturnsStructuredBadRequestForBlankTitle() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("   ")))
                .thenThrow(new InvalidAssetTitleException("title must not be blank"));

        mockMvc.perform(patch("/api/assets/{assetId}", assetId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ASSET_TITLE"))
                .andExpect(jsonPath("$.message").value("title must not be blank"));
    }

    @Test
    void updateAssetTitleReturnsStructuredBadRequestForMissingTitleField() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest(null)))
                .thenThrow(new InvalidAssetTitleException("title is required"));

        mockMvc.perform(patch("/api/assets/{assetId}", assetId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ASSET_TITLE"))
                .andExpect(jsonPath("$.message").value("title is required"));
    }

    @Test
    void updateAssetTitleReturnsStructuredBadRequestForMissingBody() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetTitleUpdateService.updateAssetTitle(assetId, null))
                .thenThrow(new InvalidAssetTitleException("title is required"));

        mockMvc.perform(patch("/api/assets/{assetId}", assetId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ASSET_TITLE"))
                .andExpect(jsonPath("$.message").value("title is required"));
    }

    @Test
    void updateAssetTitleReturnsNotFoundWhenAssetDoesNotExist() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("New Title")))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Asset not found"
                ));

        mockMvc.perform(patch("/api/assets/{assetId}", assetId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New Title"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Asset not found"));
    }

    @Test
    void updateAssetTitleReturnsNotFoundWhenAssetIsNotOwned() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("New Title")))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Asset not found"
                ));

        mockMvc.perform(patch("/api/assets/{assetId}", assetId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New Title"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Asset not found"));
    }

    @Test
    void updateAssetTitleReturnsStructuredServiceUnavailableWhenElasticsearchIsUnavailable() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("New Title")))
                .thenThrow(new ElasticsearchConnectivityException(
                        "Elasticsearch is unavailable while trying to sync search metadata for asset " + assetId,
                        new RuntimeException("connection refused")
                ));

        mockMvc.perform(patch("/api/assets/{assetId}", assetId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New Title"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ELASTICSEARCH_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        "Elasticsearch is unavailable while trying to sync search metadata for asset " + assetId
                ));
    }

    @Test
    void updateAssetTitleReturnsStructuredBadGatewayWhenElasticsearchReturnsIntegrationError() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("New Title")))
                .thenThrow(new ElasticsearchIntegrationException(
                        "Elasticsearch title sync failed for asset " + assetId + ": queue full"
                ));

        mockMvc.perform(patch("/api/assets/{assetId}", assetId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"New Title"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ELASTICSEARCH_INTEGRATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Elasticsearch title sync failed for asset " + assetId + ": queue full"
                ));
    }

    @Test
    void indexAssetReturnsStructuredServiceUnavailableWhenElasticsearchIsUnavailable() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(transcriptIndexingService.indexAssetTranscript(assetId)).thenThrow(new ElasticsearchConnectivityException(
                "Elasticsearch is unavailable while trying to bulk index transcript rows for asset " + assetId,
                new RuntimeException("connection refused")
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/assets/{assetId}/index", assetId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ELASTICSEARCH_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        "Elasticsearch is unavailable while trying to bulk index transcript rows for asset " + assetId
                ));
    }

    @Test
    void indexAssetReturnsStructuredBadGatewayWhenElasticsearchReturnsIntegrationError() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(transcriptIndexingService.indexAssetTranscript(assetId)).thenThrow(new ElasticsearchIntegrationException(
                "Elasticsearch bulk indexing failed for document " + assetId + "-row-1 with status 429: queue full"
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/assets/{assetId}/index", assetId))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ELASTICSEARCH_INTEGRATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Elasticsearch bulk indexing failed for document " + assetId + "-row-1 with status 429: queue full"
                ));
    }

    @Test
    void indexAssetReturnsNotFoundWhenAssetIsNotOwned() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(transcriptIndexingService.indexAssetTranscript(assetId))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Asset not found"
                ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/assets/{assetId}/index", assetId))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Asset not found"));
    }

    @Test
    void transcriptContextReturnsTranscriptWindow() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAssetTranscriptContext(assetId, "row-2", 2))
                .thenReturn(new AssetTranscriptContextResponse(
                        assetId,
                        "row-2",
                        2,
                        2,
                        List.of(
                                new AssetTranscriptRowResponse(
                                        "row-1",
                                        "video-1",
                                        1,
                                        "Context before",
                                        "2026-04-11T00:00:00Z"
                                ),
                                new AssetTranscriptRowResponse(
                                        "row-2",
                                        "video-1",
                                        2,
                                        "Matched row",
                                        "2026-04-11T00:00:01Z"
                                )
                        )
                ));

        mockMvc.perform(get("/api/assets/{assetId}/transcript/context", assetId)
                        .param("transcriptRowId", "row-2")
                        .param("window", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.transcriptRowId").value("row-2"))
                .andExpect(jsonPath("$.hitSegmentIndex").value(2))
                .andExpect(jsonPath("$.window").value(2))
                .andExpect(jsonPath("$.rows[0].id").value("row-1"))
                .andExpect(jsonPath("$.rows[1].id").value("row-2"));
    }

    @Test
    void transcriptContextReturnsStructuredBadRequestForInvalidWindow() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAssetTranscriptContext(assetId, "row-2", 0))
                .thenThrow(new InvalidTranscriptContextWindowException("window must be greater than 0"));

        mockMvc.perform(get("/api/assets/{assetId}/transcript/context", assetId)
                        .param("transcriptRowId", "row-2")
                        .param("window", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSCRIPT_CONTEXT_WINDOW"))
                .andExpect(jsonPath("$.message").value("window must be greater than 0"));
    }

    @Test
    void transcriptContextReturnsStructuredNotFoundForMissingTranscriptRow() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAssetTranscriptContext(assetId, "row-404", 2))
                .thenThrow(new TranscriptRowNotFoundException(assetId, "row-404"));

        mockMvc.perform(get("/api/assets/{assetId}/transcript/context", assetId)
                        .param("transcriptRowId", "row-404")
                        .param("window", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSCRIPT_ROW_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Transcript row not found for asset " + assetId + ": row-404"));
    }

    @Test
    void transcriptContextReturnsNotFoundWhenAssetIsNotOwned() throws Exception {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAssetTranscriptContext(assetId, "row-2", 2))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Asset not found"
                ));

        mockMvc.perform(get("/api/assets/{assetId}/transcript/context", assetId)
                        .param("transcriptRowId", "row-2")
                        .param("window", "2"))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Asset not found"));
    }

    @Test
    void transcriptContextReturnsStructuredBadRequestForMalformedWindowType() throws Exception {
        UUID assetId = UUID.randomUUID();

        mockMvc.perform(get("/api/assets/{assetId}/transcript/context", assetId)
                        .param("transcriptRowId", "row-2")
                        .param("window", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSCRIPT_CONTEXT_WINDOW"))
                .andExpect(jsonPath("$.message").value("window must be a valid integer"));
    }
}
