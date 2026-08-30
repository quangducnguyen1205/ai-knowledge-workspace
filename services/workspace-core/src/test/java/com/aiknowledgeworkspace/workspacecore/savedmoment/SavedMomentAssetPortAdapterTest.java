package com.aiknowledgeworkspace.workspacecore.savedmoment;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetTranscriptRowInput;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.CanonicalTranscriptStore;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentAssetPort;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentCanonicalMoment;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentTarget;
import com.aiknowledgeworkspace.workspacecore.workspace.application.port.out.WorkspaceStore;
import com.aiknowledgeworkspace.workspacecore.workspace.domain.Workspace;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The Asset module owns authorization and canonical resolution for Saved Moment. A stale, foreign
 * or removed target must resolve to nothing rather than to a different row.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace-core-saved-moment-port;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Transactional
class SavedMomentAssetPortAdapterTest {

    private static final String CURRENT_USER = "local-dev-user";

    @Autowired
    private SavedMomentAssetPort assetPort;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    private WorkspaceStore workspaceStore;

    @Autowired
    private CanonicalTranscriptStore transcriptStore;

    @BeforeEach
    void establishCurrentUserSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CURRENT_USER_ID", CURRENT_USER);
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request), true);
    }

    @AfterEach
    void clearCurrentUserSession() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void anOwnedAssetResolvesItsCanonicalPresentationAndTimingData() {
        UUID assetId = persistUpload(CURRENT_USER, List.of(
                row("row-1", 7, 1000L, 4000L, "Canonical text.")
        ));

        assertThat(assetPort.findAuthorizedMoment(assetId, "row-1")).get().satisfies(moment -> {
            assertThat(moment.assetId()).isEqualTo(assetId);
            assertThat(moment.assetTitle()).isEqualTo("Lecture");
            assertThat(moment.sourceType()).isEqualTo("UPLOAD");
            assertThat(moment.transcriptRowId()).isEqualTo("row-1");
            assertThat(moment.segmentIndex()).isEqualTo(7);
            assertThat(moment.startMs()).isEqualTo(1000L);
            assertThat(moment.endMs()).isEqualTo(4000L);
            assertThat(moment.text()).isEqualTo("Canonical text.");
        });
    }

    @Test
    void aYouTubeAssetReportsItsSourceTypeAndNullableTiming() {
        UUID assetId = persistYouTube(CURRENT_USER, List.of(row("row-1", 3, null, null, "No timing.")));

        assertThat(assetPort.findAuthorizedMoment(assetId, "row-1")).get().satisfies(moment -> {
            assertThat(moment.sourceType()).isEqualTo("YOUTUBE");
            assertThat(moment.startMs()).isNull();
            assertThat(moment.endMs()).isNull();
        });
    }

    @Test
    void aForeignAssetIsInvisibleEvenWhenTheRowIdIsCorrect() {
        UUID assetId = persistUpload("someone-else", List.of(row("row-1", 1, 0L, 1L, "Private.")));

        assertThat(assetPort.findAuthorizedMoment(assetId, "row-1")).isEmpty();
        assertThat(assetPort.findAuthorizedMoments(List.of(new SavedMomentTarget(assetId, "row-1")))).isEmpty();
    }

    @Test
    void anUnknownAssetResolvesToNothing() {
        assertThat(assetPort.findAuthorizedMoment(UUID.randomUUID(), "row-1")).isEmpty();
    }

    @Test
    void aStaleRowIdNeverFallsBackToAnotherRowAfterTranscriptReplacement() {
        UUID assetId = persistUpload(CURRENT_USER, List.of(row("row-1", 7, 1000L, 4000L, "Original.")));
        assertThat(assetPort.findAuthorizedMoment(assetId, "row-1")).isPresent();

        transcriptStore.replace(assetId, List.of(row("row-2", 7, 1000L, 4000L, "Replacement at the same segment.")));

        assertThat(assetPort.findAuthorizedMoment(assetId, "row-1")).isEmpty();
        assertThat(assetPort.findAuthorizedMoment(assetId, "row-2")).get()
                .satisfies(moment -> assertThat(moment.text()).isEqualTo("Replacement at the same segment."));
    }

    @Test
    void updatedCanonicalTextAndTimingAreReflectedForTheSameRowIdentity() {
        UUID assetId = persistUpload(CURRENT_USER, List.of(row("row-1", 7, 1000L, 4000L, "Original.")));

        transcriptStore.replace(assetId, List.of(row("row-1", 9, 9000L, 9500L, "Edited.")));

        assertThat(assetPort.findAuthorizedMoment(assetId, "row-1")).get().satisfies(moment -> {
            assertThat(moment.text()).isEqualTo("Edited.");
            assertThat(moment.segmentIndex()).isEqualTo(9);
            assertThat(moment.startMs()).isEqualTo(9000L);
        });
    }

    @Test
    void aRowWithoutStoredIdentityKeepsTheSegmentConventionWithoutBecomingAFallback() {
        UUID assetId = persistUpload(CURRENT_USER, List.of(
                row(null, 4, 4000L, 4500L, "Legacy identity."),
                row("row-9", 9, 9000L, 9500L, "Stable identity.")
        ));

        assertThat(assetPort.findAuthorizedMoment(assetId, "segment-4")).get()
                .satisfies(moment -> assertThat(moment.text()).isEqualTo("Legacy identity."));
        assertThat(assetPort.findAuthorizedMoment(assetId, "segment-9")).isEmpty();
        assertThat(assetPort.findAuthorizedMoment(assetId, "row-9")).get()
                .satisfies(moment -> assertThat(moment.text()).isEqualTo("Stable identity."));
    }

    @Test
    void aBlankOrUnusableRowIsNeverResolvable() {
        UUID assetId = persistUpload(CURRENT_USER, List.of(
                row("blank", 2, null, null, "   "),
                row("usable", 3, null, null, "Usable.")
        ));

        assertThat(assetPort.findAuthorizedMoment(assetId, "blank")).isEmpty();
        assertThat(assetPort.findAuthorizedMoment(assetId, "usable")).isPresent();
    }

    @Test
    void batchResolutionKeepsAssetsIndependentAndDropsUnresolvableTargets() {
        UUID ownedAsset = persistUpload(CURRENT_USER, List.of(
                row("row-a", 1, 0L, 1000L, "Owned A."),
                row("row-b", 2, 1000L, 2000L, "Owned B.")
        ));
        UUID otherOwnedAsset = persistUpload(CURRENT_USER, List.of(row("row-a", 1, 0L, 1000L, "Second asset.")));
        UUID foreignAsset = persistUpload("someone-else", List.of(row("row-a", 1, 0L, 1000L, "Foreign.")));

        List<SavedMomentCanonicalMoment> moments = assetPort.findAuthorizedMoments(List.of(
                new SavedMomentTarget(ownedAsset, "row-a"),
                new SavedMomentTarget(ownedAsset, "row-b"),
                new SavedMomentTarget(ownedAsset, "row-missing"),
                new SavedMomentTarget(otherOwnedAsset, "row-a"),
                new SavedMomentTarget(foreignAsset, "row-a"),
                new SavedMomentTarget(UUID.randomUUID(), "row-a")
        ));

        assertThat(moments).hasSize(3);
        assertThat(moments).extracting(SavedMomentCanonicalMoment::text)
                .containsExactlyInAnyOrder("Owned A.", "Owned B.", "Second asset.");
        assertThat(moments).extracting(SavedMomentCanonicalMoment::assetId)
                .doesNotContain(foreignAsset);
    }

    @Test
    void emptyOrNullTargetsResolveToAnEmptyResult() {
        assertThat(assetPort.findAuthorizedMoments(List.of())).isEmpty();
        assertThat(assetPort.findAuthorizedMoments(null)).isEmpty();
    }

    private AssetTranscriptRowInput row(String id, int segmentIndex, Long startMs, Long endMs, String text) {
        return new AssetTranscriptRowInput(
                id, "video-1", segmentIndex, startMs, endMs, text, "2026-07-30T00:00:00Z"
        );
    }

    private UUID persistUpload(String ownerId, List<AssetTranscriptRowInput> rows) {
        UUID workspaceId = workspaceStore
                .save(new Workspace(UUID.randomUUID(), "Saved moments", ownerId, false)).getId();
        UUID assetId = UUID.randomUUID();
        assetStore.save(Asset.uploaded(
                assetId, "lecture.mp4", "Lecture", AssetStatus.SEARCHABLE, workspaceId,
                "workspace-media", "objects/" + assetId + ".mp4", "video/mp4", 42L, "etag-1"
        ));
        transcriptStore.replace(assetId, rows);
        return assetId;
    }

    private UUID persistYouTube(String ownerId, List<AssetTranscriptRowInput> rows) {
        UUID workspaceId = workspaceStore
                .save(new Workspace(UUID.randomUUID(), "Saved moments", ownerId, false)).getId();
        UUID assetId = UUID.randomUUID();
        assetStore.saveYoutube(Asset.youtube(
                assetId, "abc_DEF-123", "Lecture", AssetStatus.SEARCHABLE, workspaceId
        ));
        transcriptStore.replace(assetId, rows);
        return assetId;
    }
}
