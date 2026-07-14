package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.application.lifecycle.AssetWorkspaceUsageService;
import com.aiknowledgeworkspace.workspacecore.asset.infrastructure.persistence.AssetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetWorkspaceUsageServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Test
    void workspaceHasAssetsReturnsFalseForEmptyWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        when(assetRepository.countByWorkspace_Id(workspaceId)).thenReturn(0L);

        assertThat(service().workspaceHasAssets(workspaceId)).isFalse();

        verify(assetRepository).countByWorkspace_Id(workspaceId);
    }

    @Test
    void workspaceHasAssetsReturnsTrueWhenAnyAssetBelongsToWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        when(assetRepository.countByWorkspace_Id(workspaceId)).thenReturn(1L);

        assertThat(service().workspaceHasAssets(workspaceId)).isTrue();

        verify(assetRepository).countByWorkspace_Id(workspaceId);
    }

    private AssetWorkspaceUsageService service() {
        return new AssetWorkspaceUsageService(assetRepository);
    }
}
