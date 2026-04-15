package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aiknowledgeworkspace.workspacecore.common.config.ElasticsearchProperties;
import com.aiknowledgeworkspace.workspacecore.search.ElasticsearchIntegrationException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import com.aiknowledgeworkspace.workspacecore.workspace.Workspace;

@ExtendWith(MockitoExtension.class)
class AssetDeletionServiceTest {

    @Mock
    private AssetService assetService;

    @Mock
    private AssetPersistenceService assetPersistenceService;

    private MockRestServiceServer mockServer;
    private AssetDeletionService assetDeletionService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:9201");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        ElasticsearchProperties properties = new ElasticsearchProperties();
        properties.setBaseUrl("http://localhost:9201");
        properties.setTranscriptIndexName("asset-transcript-rows");

        assetDeletionService = new AssetDeletionService(
                assetService,
                assetPersistenceService,
                builder.build(),
                properties
        );
    }

    @Test
    void deletingProcessingAssetRemovesLocalRecordsWithoutElasticsearchCleanup() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.PROCESSING);

        when(assetService.getAsset(assetId)).thenReturn(asset);

        assetDeletionService.deleteAsset(assetId);

        verify(assetPersistenceService).deleteAssetRecords(asset);
        mockServer.verify();
    }

    @Test
    void deletingTranscriptReadyAssetRemovesLocalRecordsWithoutElasticsearchCleanup() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.TRANSCRIPT_READY);

        when(assetService.getAsset(assetId)).thenReturn(asset);

        assetDeletionService.deleteAsset(assetId);

        verify(assetPersistenceService).deleteAssetRecords(asset);
        mockServer.verify();
    }

    @Test
    void deletingSearchableAssetCleansElasticsearchThenRemovesLocalRecords() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.SEARCHABLE);

        when(assetService.getAsset(assetId)).thenReturn(asset);
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_delete_by_query?refresh=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"assetId.keyword\":\"" + assetId + "\"")))
                .andRespond(withSuccess());

        assetDeletionService.deleteAsset(assetId);

        verify(assetPersistenceService).deleteAssetRecords(asset);
        mockServer.verify();
    }

    @Test
    void deletingSearchableAssetStopsWhenElasticsearchCleanupFails() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.SEARCHABLE);

        when(assetService.getAsset(assetId)).thenReturn(asset);
        mockServer.expect(once(), requestTo("http://localhost:9201/asset-transcript-rows/_delete_by_query?refresh=true"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> assetDeletionService.deleteAsset(assetId))
                .isInstanceOf(ElasticsearchIntegrationException.class)
                .hasMessageContaining("Elasticsearch returned HTTP 500");

        verify(assetPersistenceService, never()).deleteAssetRecords(asset);
        mockServer.verify();
    }

    @Test
    void deletingFailedAssetRemovesLocalRecordsWithoutElasticsearchCleanup() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.FAILED);

        when(assetService.getAsset(assetId)).thenReturn(asset);

        assetDeletionService.deleteAsset(assetId);

        verify(assetPersistenceService).deleteAssetRecords(asset);
        mockServer.verify();
    }

    @Test
    void deletingMissingAssetReturnsNotFound() {
        UUID assetId = UUID.randomUUID();
        when(assetService.getAsset(assetId)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

        assertThatThrownBy(() -> assetDeletionService.deleteAsset(assetId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(((ResponseStatusException) exception).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void repeatedDeleteReturnsNotFoundAfterAssetIsGone() {
        UUID assetId = UUID.randomUUID();
        Asset asset = asset(assetId, AssetStatus.PROCESSING);

        when(assetService.getAsset(assetId))
                .thenReturn(asset)
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));

        assetDeletionService.deleteAsset(assetId);

        assertThatThrownBy(() -> assetDeletionService.deleteAsset(assetId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(((ResponseStatusException) exception).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verify(assetPersistenceService).deleteAssetRecords(asset);
    }

    private Asset asset(UUID assetId, AssetStatus status) {
        Asset asset = new Asset("lecture.mp4", "Lecture", status, new Workspace(UUID.randomUUID(), "Workspace"));
        ReflectionTestUtils.setField(asset, "id", assetId);
        return asset;
    }
}
