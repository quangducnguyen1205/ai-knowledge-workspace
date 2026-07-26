package com.aiknowledgeworkspace.workspacecore.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AssetSourceModelTest {

    @Test
    void uploadedFactoryCreatesOnlyTheUploadShape() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        Asset asset = uploaded(assetId, workspaceId, 0L);

        assertThat(asset.getId()).isEqualTo(assetId);
        assertThat(asset.getSourceType()).isEqualTo(AssetSourceType.UPLOAD);
        assertThat(asset.getYoutubeVideoId()).isNull();
        assertThat(asset.getOriginalFilename()).isEqualTo("lecture.mp4");
        assertThat(asset.getStorageBucket()).isEqualTo("workspace-media");
        assertThat(asset.getObjectKey()).isEqualTo("objects/lecture.mp4");
        assertThat(asset.getContentType()).isEqualTo("video/mp4");
        assertThat(asset.getSizeBytes()).isZero();
    }

    @Test
    void youtubeFactoryCreatesOnlyTheYoutubeShape() {
        UUID assetId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        Asset asset = Asset.youtube(
                assetId, "video-id", "Lecture", AssetStatus.PROCESSING, workspaceId
        );

        assertThat(asset.getId()).isEqualTo(assetId);
        assertThat(asset.getSourceType()).isEqualTo(AssetSourceType.YOUTUBE);
        assertThat(asset.getYoutubeVideoId()).isEqualTo("video-id");
        assertThat(asset.getOriginalFilename()).isNull();
        assertThat(asset.getStorageBucket()).isNull();
        assertThat(asset.getObjectKey()).isNull();
        assertThat(asset.getContentType()).isNull();
        assertThat(asset.getSizeBytes()).isNull();
        assertThat(asset.getEtag()).isNull();
    }

    @Test
    void creationRejectsInvalidSourceAndLifecycleValues() {
        UUID workspaceId = UUID.randomUUID();

        assertThatThrownBy(() -> uploaded(UUID.randomUUID(), workspaceId, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sizeBytes");
        assertThatThrownBy(() -> Asset.youtube(
                UUID.randomUUID(), "   ", "Lecture", AssetStatus.PROCESSING, workspaceId
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("youtubeVideoId");
        assertThatThrownBy(() -> Asset.youtube(
                UUID.randomUUID(), "video id", "Lecture", AssetStatus.PROCESSING, workspaceId
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whitespace");
        assertThatThrownBy(() -> Asset.youtube(
                UUID.randomUUID(), "x".repeat(129), "Lecture", AssetStatus.PROCESSING, workspaceId
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("128");
        assertThatThrownBy(() -> Asset.youtube(
                UUID.randomUUID(), "video-id", "   ", AssetStatus.PROCESSING, workspaceId
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> Asset.youtube(
                UUID.randomUUID(), "video-id", "Lecture", null, workspaceId
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status");
        assertThatThrownBy(() -> Asset.youtube(
                UUID.randomUUID(), "video-id", "Lecture", AssetStatus.PROCESSING, null
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("workspaceId");
    }

    @Test
    void lifecycleGuardRejectsReflectedMixedStateAndNoPublicConstructorBypassesFactories() {
        Asset upload = uploaded(UUID.randomUUID(), UUID.randomUUID(), 42L);
        ReflectionTestUtils.setField(upload, "youtubeVideoId", "mixed-video");

        assertThatThrownBy(upload::onCreate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not have youtubeVideoId");

        Asset youtube = Asset.youtube(
                UUID.randomUUID(), "video-id", "Lecture", AssetStatus.PROCESSING, UUID.randomUUID()
        );
        ReflectionTestUtils.setField(youtube, "storageBucket", "workspace-media");

        assertThatThrownBy(youtube::onCreate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not have upload storage fields");
        assertThat(Asset.class.getConstructors()).isEmpty();
    }

    private Asset uploaded(UUID assetId, UUID workspaceId, long sizeBytes) {
        return Asset.uploaded(
                assetId,
                "lecture.mp4",
                "Lecture",
                AssetStatus.PROCESSING,
                workspaceId,
                "workspace-media",
                "objects/lecture.mp4",
                "video/mp4",
                sizeBytes,
                "etag-1"
        );
    }
}
