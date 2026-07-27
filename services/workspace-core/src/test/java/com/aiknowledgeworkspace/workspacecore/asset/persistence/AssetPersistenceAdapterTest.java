package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.exception.DuplicateYouTubeAssetException;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AssetPersistenceAdapterTest {

    private final AssetJpaRepository repository = mock(AssetJpaRepository.class);
    private final AssetPersistenceAdapter adapter = new AssetPersistenceAdapter(repository);

    @Test
    void translatesOnlyTheWorkspaceYoutubeIdentityConstraintRace() {
        Asset asset = youtubeAsset();
        ConstraintViolationException constraint = mock(ConstraintViolationException.class);
        when(constraint.getConstraintName()).thenReturn("uk_assets_workspace_youtube_video");
        when(repository.saveAndFlush(asset)).thenThrow(new DataIntegrityViolationException("race", constraint));

        assertThatThrownBy(() -> adapter.saveYoutube(asset))
                .isInstanceOf(DuplicateYouTubeAssetException.class)
                .hasMessage("This YouTube video already exists in the workspace");
    }

    @Test
    void preservesUnrelatedIntegrityViolations() {
        Asset asset = youtubeAsset();
        ConstraintViolationException constraint = mock(ConstraintViolationException.class);
        when(constraint.getConstraintName()).thenReturn("ck_assets_source_shape");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("shape", constraint);
        when(repository.saveAndFlush(asset)).thenThrow(failure);

        assertThatThrownBy(() -> adapter.saveYoutube(asset)).isSameAs(failure);
    }

    private Asset youtubeAsset() {
        return Asset.youtube(
                UUID.randomUUID(),
                "abc_DEF-123",
                "YouTube video",
                AssetStatus.PROCESSING,
                UUID.randomUUID()
        );
    }
}
