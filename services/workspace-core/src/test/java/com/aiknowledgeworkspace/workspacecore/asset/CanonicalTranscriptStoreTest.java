package com.aiknowledgeworkspace.workspacecore.asset;

import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowView;

import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-canonical-transcript;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Transactional
class CanonicalTranscriptStoreTest {

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private CanonicalTranscriptStore transcriptStore;

    @Test
    void replacingAnExistingSnapshotDeletesOldRowsBeforeInsertingTheSameSegmentIdentities() {
        Workspace workspace = workspaceStore.save(new Workspace(
                UUID.randomUUID(), "Architecture", "owner-1", false
        ));
        Asset asset = assetStore.save(new Asset(
                UUID.randomUUID(),
                "lecture.mp4",
                "Lecture",
                AssetStatus.TRANSCRIPT_READY,
                workspace.getId(),
                "workspace-media",
                "users/owner-1/assets/lecture.mp4",
                "video/mp4",
                42L,
                null
        ));

        transcriptStore.replace(asset.getId(), List.of(row("Original")));
        List<AssetTranscriptRowView> replacement = transcriptStore.replace(
                asset.getId(), List.of(row("Replacement"))
        );

        assertThat(replacement).extracting(AssetTranscriptRowView::text)
                .containsExactly("Replacement");
        assertThat(transcriptStore.load(asset.getId()))
                .extracting(AssetTranscriptRowView::text)
                .containsExactly("Replacement");
    }

    private AssetTranscriptRowInput row(String text) {
        return new AssetTranscriptRowInput(
                "row-1", "video-1", 0, text, "2026-07-20T00:00:00Z"
        );
    }
}
