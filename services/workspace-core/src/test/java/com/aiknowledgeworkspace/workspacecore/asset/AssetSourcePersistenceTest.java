package com.aiknowledgeworkspace.workspacecore.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetSourceType;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.DuplicateYouTubeAssetException;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-asset-source;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Transactional
class AssetSourcePersistenceTest {

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private EntityManager entityManager;

    @Test
    void uploadAndYoutubeSourcesRoundTripThroughDetailAndListQueries() {
        Workspace workspace = workspaceStore.save(new Workspace(
                UUID.randomUUID(), "Source-aware assets", "owner-1", false
        ));
        UUID uploadId = UUID.randomUUID();
        UUID youtubeId = UUID.randomUUID();

        assetStore.save(Asset.uploaded(
                uploadId,
                "lecture.mp4",
                "Uploaded lecture",
                AssetStatus.PROCESSING,
                workspace.getId(),
                "workspace-media",
                "objects/lecture.mp4",
                "video/mp4",
                42L,
                "etag-1"
        ));
        assetStore.save(Asset.youtube(
                youtubeId,
                "video-id",
                "YouTube lecture",
                AssetStatus.PROCESSING,
                workspace.getId()
        ));
        entityManager.flush();
        entityManager.clear();

        Asset upload = assetStore.findById(uploadId).orElseThrow();
        assertThat(upload.getSourceType()).isEqualTo(AssetSourceType.UPLOAD);
        assertThat(upload.getYoutubeVideoId()).isNull();
        assertThat(upload.getOriginalFilename()).isEqualTo("lecture.mp4");
        assertThat(upload.getStorageBucket()).isEqualTo("workspace-media");
        assertThat(upload.getObjectKey()).isEqualTo("objects/lecture.mp4");
        assertThat(upload.getContentType()).isEqualTo("video/mp4");
        assertThat(upload.getSizeBytes()).isEqualTo(42L);

        Asset youtube = assetStore.findById(youtubeId).orElseThrow();
        assertThat(youtube.getSourceType()).isEqualTo(AssetSourceType.YOUTUBE);
        assertThat(youtube.getYoutubeVideoId()).isEqualTo("video-id");
        assertThat(youtube.getOriginalFilename()).isNull();
        assertThat(youtube.getStorageBucket()).isNull();
        assertThat(youtube.getObjectKey()).isNull();
        assertThat(youtube.getContentType()).isNull();
        assertThat(youtube.getSizeBytes()).isNull();
        assertThat(youtube.getEtag()).isNull();

        List<Asset> listed = assetStore.findByWorkspaceId(workspace.getId());
        assertThat(listed)
                .extracting(Asset::getSourceType)
                .containsExactlyInAnyOrder(AssetSourceType.UPLOAD, AssetSourceType.YOUTUBE);
        assertThat(listed)
                .filteredOn(asset -> asset.getSourceType() == AssetSourceType.YOUTUBE)
                .singleElement()
                .extracting(Asset::getYoutubeVideoId)
                .isEqualTo("video-id");
    }

    @Test
    void workspaceScopedDuplicateRaceIsTranslatedByTheYoutubePersistenceBoundary() {
        Workspace workspace = workspaceStore.save(new Workspace(
                UUID.randomUUID(), "Duplicate identity", "owner-1", false
        ));
        assetStore.saveYoutube(Asset.youtube(
                UUID.randomUUID(),
                "abc_DEF-123",
                "First",
                AssetStatus.FAILED,
                workspace.getId()
        ));

        assertThatThrownBy(() -> assetStore.saveYoutube(Asset.youtube(
                UUID.randomUUID(),
                "abc_DEF-123",
                "Duplicate",
                AssetStatus.PROCESSING,
                workspace.getId()
        )))
                .isInstanceOf(DuplicateYouTubeAssetException.class);
    }
}
