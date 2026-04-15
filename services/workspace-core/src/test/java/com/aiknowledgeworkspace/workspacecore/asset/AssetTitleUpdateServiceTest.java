package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchIntegrationException;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AssetTitleUpdateServiceTest {

    @Mock
    private AssetService assetService;

    @Mock
    private AssetPersistenceService assetPersistenceService;

    private MockRestServiceServer mockServer;
    private AssetTitleUpdateService assetTitleUpdateService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:9201");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl("http://localhost:9201");
        properties.setTranscriptIndexName("asset-transcript-rows");

        assetTitleUpdateService = new AssetTitleUpdateService(
                assetService,
                assetPersistenceService,
                builder.build(),
                properties
        );
    }

    @Test
    void updateTitleSucceedsForNonSearchableAsset() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, "Old Title", AssetStatus.TRANSCRIPT_READY);
        Asset updatedAsset = asset(assetId, "New Title", AssetStatus.TRANSCRIPT_READY);

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(assetPersistenceService.updateAssetTitle(asset, "New Title")).thenReturn(updatedAsset);

        Asset response = assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("  New Title  "));

        assertThat(response.getTitle()).isEqualTo("New Title");
        verify(assetPersistenceService).updateAssetTitle(asset, "New Title");
        mockServer.verify();
    }

    @Test
    void updateTitleSucceedsForSearchableAssetAfterElasticsearchSync() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, "Old Title", AssetStatus.SEARCHABLE);
        Asset updatedAsset = asset(assetId, "New Title", AssetStatus.SEARCHABLE);

        when(assetService.getAsset(assetId)).thenReturn(asset);
        when(assetPersistenceService.updateAssetTitle(asset, "New Title")).thenReturn(updatedAsset);

        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_update_by_query?refresh=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"assetId.keyword\":\"" + assetId + "\"")))
                .andExpect(content().string(containsString("\"assetTitle\":\"New Title\"")))
                .andRespond(withSuccess("""
                        {
                          "total": 2,
                          "updated": 2,
                          "version_conflicts": 0,
                          "failures": []
                        }
                        """, MediaType.APPLICATION_JSON));

        Asset response = assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("New Title"));

        assertThat(response.getTitle()).isEqualTo("New Title");
        verify(assetPersistenceService).updateAssetTitle(asset, "New Title");
        mockServer.verify();
    }

    @Test
    void unchangedTitleReturnsSuccessAsNoOp() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, "Lecture 1", AssetStatus.SEARCHABLE);

        when(assetService.getAsset(assetId)).thenReturn(asset);

        Asset response = assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("  Lecture 1 "));

        assertThat(response).isSameAs(asset);
        verifyNoInteractions(assetPersistenceService);
        mockServer.verify();
    }

    @Test
    void blankTitleReturnsBadRequest() {
        assertThatThrownBy(() -> assetTitleUpdateService.updateAssetTitle(
                UUID.randomUUID(),
                new UpdateAssetTitleRequest("   ")
        )).isInstanceOf(InvalidAssetTitleException.class)
                .hasMessage("title must not be blank");

        verifyNoInteractions(assetService, assetPersistenceService);
    }

    @Test
    void overLimitTitleReturnsBadRequest() {
        String tooLongTitle = "x".repeat(256);

        assertThatThrownBy(() -> assetTitleUpdateService.updateAssetTitle(
                UUID.randomUUID(),
                new UpdateAssetTitleRequest(tooLongTitle)
        )).isInstanceOf(InvalidAssetTitleException.class)
                .hasMessage("title must be less than or equal to 255 characters");

        verifyNoInteractions(assetService, assetPersistenceService);
    }

    @Test
    void assetNotFoundReturnsNotFound() {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAsset(assetId)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

        assertThatThrownBy(() -> assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("New Title")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) exception;
                    assertThat(responseStatusException.getStatusCode().value()).isEqualTo(404);
                    assertThat(responseStatusException.getReason()).isEqualTo("Asset not found");
                });

        verifyNoInteractions(assetPersistenceService);
    }

    @Test
    void searchableAssetTitleUpdateFailsCleanlyWhenElasticsearchSyncFails() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, "Old Title", AssetStatus.SEARCHABLE);

        when(assetService.getAsset(assetId)).thenReturn(asset);
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_update_by_query?refresh=true"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("New Title")))
                .isInstanceOf(ElasticsearchIntegrationException.class)
                .hasMessageContaining("Elasticsearch returned HTTP 500");

        verify(assetPersistenceService, never()).updateAssetTitle(asset, "New Title");
        mockServer.verify();
    }

    @Test
    void failedSearchMetadataSyncDoesNotUpdateDatabaseTitle() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, "Old Title", AssetStatus.SEARCHABLE);

        when(assetService.getAsset(assetId)).thenReturn(asset);
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_update_by_query?refresh=true"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "total": 0,
                          "updated": 0,
                          "version_conflicts": 0,
                          "failures": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> assetTitleUpdateService.updateAssetTitle(assetId, new UpdateAssetTitleRequest("New Title")))
                .isInstanceOf(ElasticsearchIntegrationException.class)
                .hasMessage("Elasticsearch title sync did not match any transcript documents for asset " + assetId);

        verify(assetPersistenceService, never()).updateAssetTitle(asset, "New Title");
        mockServer.verify();
    }

    private Asset asset(UUID assetId, String title, AssetStatus status) {
        Asset asset = new Asset("lecture.mp4", title, status, new Workspace(UUID.randomUUID(), "Study Workspace"));
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }
}
