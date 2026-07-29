package com.aiknowledgeworkspace.workspacecore.asset.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.asset.application.command.SaveAssetPlaybackProgressCommand;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.AssetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.asset.application.exception.InvalidPlaybackProgressException;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetPlaybackProgressSnapshot;
import com.aiknowledgeworkspace.workspacecore.asset.application.port.out.AssetPlaybackProgressStore;
import com.aiknowledgeworkspace.workspacecore.asset.application.result.AssetPlaybackProgressView;
import com.aiknowledgeworkspace.workspacecore.asset.domain.Asset;
import com.aiknowledgeworkspace.workspacecore.asset.domain.AssetStatus;
import com.aiknowledgeworkspace.workspacecore.identity.api.CurrentUserContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

class AssetPlaybackProgressApplicationServiceTest {

    private static final String CURRENT_USER = "user-1";
    private static final Instant SAVED_AT = Instant.parse("2026-07-29T08:00:00Z");

    private AssetQueryApplicationService assetQueries;
    private AssetPlaybackProgressStore progressStore;
    private AssetPlaybackProgressTransaction progressTransaction;
    private CurrentUserContext currentUser;
    private AssetPlaybackProgressApplicationService service;
    private UUID assetId;

    @BeforeEach
    void setUp() {
        assetQueries = mock(AssetQueryApplicationService.class);
        progressStore = mock(AssetPlaybackProgressStore.class);
        progressTransaction = mock(AssetPlaybackProgressTransaction.class);
        currentUser = mock(CurrentUserContext.class);
        service = new AssetPlaybackProgressApplicationService(
                assetQueries, progressStore, progressTransaction, currentUser
        );
        assetId = UUID.randomUUID();
    }

    // ----------------------------------------------------------------- read

    @Test
    void absentProgressReturnsTheFrozenUnstartedRepresentationWithoutWriting() {
        authorize(uploadAsset(AssetStatus.SEARCHABLE));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        when(progressStore.find(assetId, CURRENT_USER)).thenReturn(Optional.empty());

        AssetPlaybackProgressView result = service.getProgress(assetId);

        assertThat(result).isEqualTo(new AssetPlaybackProgressView(assetId, 0L, false, null));
        verify(progressStore).find(assetId, CURRENT_USER);
        verifyNoInteractions(progressTransaction);
    }

    @Test
    void storedProgressIsReturnedForTheCurrentUser() {
        authorize(uploadAsset(AssetStatus.SEARCHABLE));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        when(progressStore.find(assetId, CURRENT_USER))
                .thenReturn(Optional.of(new AssetPlaybackProgressSnapshot(12345L, false, SAVED_AT)));

        assertThat(service.getProgress(assetId))
                .isEqualTo(new AssetPlaybackProgressView(assetId, 12345L, false, SAVED_AT));
    }

    @Test
    void completedProgressIsReturnedUnchangedIncludingItsLastPosition() {
        authorize(uploadAsset(AssetStatus.SEARCHABLE));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        when(progressStore.find(assetId, CURRENT_USER))
                .thenReturn(Optional.of(new AssetPlaybackProgressSnapshot(53480L, true, SAVED_AT)));

        AssetPlaybackProgressView result = service.getProgress(assetId);

        assertThat(result.completed()).isTrue();
        assertThat(result.positionMs()).isEqualTo(53480L);
        assertThat(result.updatedAt()).isEqualTo(SAVED_AT);
    }

    @Test
    void youtubeAssetsUseTheSameSourceNeutralProgressPath() {
        Asset youtube = Asset.youtube(assetId, "abc_DEF-123", "YouTube video", AssetStatus.PROCESSING, UUID.randomUUID());
        authorize(youtube);
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        when(progressStore.find(assetId, CURRENT_USER))
                .thenReturn(Optional.of(new AssetPlaybackProgressSnapshot(700L, false, SAVED_AT)));

        assertThat(service.getProgress(assetId).positionMs()).isEqualTo(700L);
    }

    @ParameterizedTest
    @EnumSource(AssetStatus.class)
    void noProcessingStatusBlocksReadingProgress(AssetStatus status) {
        authorize(uploadAsset(status));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        when(progressStore.find(assetId, CURRENT_USER))
                .thenReturn(Optional.of(new AssetPlaybackProgressSnapshot(4200L, false, SAVED_AT)));

        assertThat(service.getProgress(assetId).positionMs()).isEqualTo(4200L);
    }

    @Test
    void authorizationRunsBeforeAnyProgressRepositoryAccess() {
        authorize(uploadAsset(AssetStatus.PROCESSING));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        when(progressStore.find(assetId, CURRENT_USER)).thenReturn(Optional.empty());

        service.getProgress(assetId);

        InOrder order = inOrder(assetQueries, progressStore);
        order.verify(assetQueries).loadAuthorizedAsset(assetId);
        order.verify(progressStore).find(assetId, CURRENT_USER);
    }

    @Test
    void unauthorizedOrMissingAssetIsRejectedBeforeTouchingProgressStorage() {
        when(assetQueries.loadAuthorizedAsset(assetId)).thenThrow(new AssetNotFoundException());

        assertThatThrownBy(() -> service.getProgress(assetId)).isInstanceOf(AssetNotFoundException.class);

        verifyNoInteractions(progressStore, progressTransaction);
    }

    // ---------------------------------------------------------------- write

    @Test
    void saveCreatesProgressForTheCurrentUserAndAsset() {
        authorize(uploadAsset(AssetStatus.PROCESSING));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        whenUpsertEchoes();

        AssetPlaybackProgressView result = service.saveProgress(assetId, command(12345, false));

        assertThat(result.assetId()).isEqualTo(assetId);
        assertThat(result.positionMs()).isEqualTo(12345L);
        assertThat(result.completed()).isFalse();
        verify(progressTransaction).upsert(
                org.mockito.ArgumentMatchers.eq(assetId),
                org.mockito.ArgumentMatchers.eq(CURRENT_USER),
                org.mockito.ArgumentMatchers.eq(12345L),
                org.mockito.ArgumentMatchers.eq(false),
                any(Instant.class)
        );
    }

    @Test
    void saveUpdatesAnExistingPositionWithLastWriteWins() {
        authorize(uploadAsset(AssetStatus.SEARCHABLE));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        whenUpsertEchoes();

        assertThat(service.saveProgress(assetId, command(1000, false)).positionMs()).isEqualTo(1000L);
        assertThat(service.saveProgress(assetId, command(250, false)).positionMs()).isEqualTo(250L);

        verify(progressTransaction, times(2)).upsert(
                org.mockito.ArgumentMatchers.eq(assetId), anyString(), anyLong(), anyBoolean(), any(Instant.class)
        );
    }

    @Test
    void saveAcceptsZero() {
        authorize(uploadAsset(AssetStatus.SEARCHABLE));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        whenUpsertEchoes();

        assertThat(service.saveProgress(assetId, command(0, false)).positionMs()).isZero();
    }

    @Test
    void savePersistsCompletionStateWithItsLastPosition() {
        authorize(uploadAsset(AssetStatus.SEARCHABLE));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        whenUpsertEchoes();

        AssetPlaybackProgressView result = service.saveProgress(assetId, command(53480, true));

        assertThat(result.completed()).isTrue();
        assertThat(result.positionMs()).isEqualTo(53480L);
    }

    @Test
    void saveReturnsThePersistedTimestamp() {
        authorize(uploadAsset(AssetStatus.SEARCHABLE));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        when(progressTransaction.upsert(any(), anyString(), anyLong(), anyBoolean(), any(Instant.class)))
                .thenReturn(new AssetPlaybackProgressSnapshot(10L, false, SAVED_AT));

        assertThat(service.saveProgress(assetId, command(10, false)).updatedAt()).isEqualTo(SAVED_AT);
    }

    @Test
    void repeatingAnIdenticalRequestStaysSafeAndReturnsTheSameRepresentation() {
        authorize(uploadAsset(AssetStatus.SEARCHABLE));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        whenUpsertEchoes();

        AssetPlaybackProgressView first = service.saveProgress(assetId, command(12345, false));
        AssetPlaybackProgressView second = service.saveProgress(assetId, command(12345, false));

        assertThat(second.assetId()).isEqualTo(first.assetId());
        assertThat(second.positionMs()).isEqualTo(first.positionMs());
        assertThat(second.completed()).isEqualTo(first.completed());
    }

    @Test
    void progressIsIsolatedByUserAndAsset() {
        Asset asset = uploadAsset(AssetStatus.SEARCHABLE);
        UUID otherAssetId = UUID.randomUUID();
        Asset otherAsset = Asset.uploaded(
                otherAssetId, "other.mp4", "Other", AssetStatus.SEARCHABLE, UUID.randomUUID(),
                "workspace-media", "objects/other.mp4", "video/mp4", 9L, null
        );
        when(assetQueries.loadAuthorizedAsset(assetId)).thenReturn(asset);
        when(assetQueries.loadAuthorizedAsset(otherAssetId)).thenReturn(otherAsset);
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER, "user-2");
        when(progressStore.find(assetId, CURRENT_USER))
                .thenReturn(Optional.of(new AssetPlaybackProgressSnapshot(111L, false, SAVED_AT)));
        when(progressStore.find(otherAssetId, "user-2")).thenReturn(Optional.empty());

        assertThat(service.getProgress(assetId).positionMs()).isEqualTo(111L);
        assertThat(service.getProgress(otherAssetId).positionMs()).isZero();

        verify(progressStore).find(assetId, CURRENT_USER);
        verify(progressStore).find(otherAssetId, "user-2");
    }

    @Test
    void negativePositionIsRejectedBeforeAuthorizationAndPersistence() {
        assertThatThrownBy(() -> service.saveProgress(assetId, command(-1, false)))
                .isInstanceOf(InvalidPlaybackProgressException.class)
                .hasMessage("positionMs must be greater than or equal to 0");

        verifyNoInteractions(assetQueries, progressStore, progressTransaction, currentUser);
    }

    @Test
    void missingPositionIsRejected() {
        assertThatThrownBy(() -> service.saveProgress(assetId, new SaveAssetPlaybackProgressCommand(null, false)))
                .isInstanceOf(InvalidPlaybackProgressException.class)
                .hasMessage("positionMs is required");

        assertThatThrownBy(() -> service.saveProgress(assetId, null))
                .isInstanceOf(InvalidPlaybackProgressException.class)
                .hasMessage("positionMs is required");

        verifyNoInteractions(assetQueries, progressStore, progressTransaction);
    }

    @Test
    void nonIntegerPositionIsRejectedInsteadOfBeingTruncated() {
        assertThatThrownBy(() -> service.saveProgress(
                assetId, new SaveAssetPlaybackProgressCommand(new BigDecimal("12.5"), false)
        ))
                .isInstanceOf(InvalidPlaybackProgressException.class)
                .hasMessage("positionMs must be a whole number of milliseconds");

        verifyNoInteractions(assetQueries, progressStore, progressTransaction);
    }

    @Test
    void trailingZeroDecimalsRemainAValidWholeMillisecondValue() {
        authorize(uploadAsset(AssetStatus.SEARCHABLE));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        whenUpsertEchoes();

        assertThat(service.saveProgress(
                assetId, new SaveAssetPlaybackProgressCommand(new BigDecimal("12345.00"), false)
        ).positionMs()).isEqualTo(12345L);
    }

    @Test
    void anOutOfRangePositionIsRejectedSafely() {
        assertThatThrownBy(() -> service.saveProgress(
                assetId,
                new SaveAssetPlaybackProgressCommand(new BigDecimal("99999999999999999999999"), false)
        ))
                .isInstanceOf(InvalidPlaybackProgressException.class)
                .hasMessage("positionMs is outside the supported range");

        verifyNoInteractions(assetQueries, progressStore, progressTransaction);
    }

    @Test
    void anUnauthorizedSaveNeverReachesPersistence() {
        when(assetQueries.loadAuthorizedAsset(assetId)).thenThrow(new AssetNotFoundException());

        assertThatThrownBy(() -> service.saveProgress(assetId, command(10, false)))
                .isInstanceOf(AssetNotFoundException.class);

        verifyNoInteractions(progressStore, progressTransaction);
    }

    @Test
    void aMissingCompletionFlagIsRejectedInsteadOfDefaultingToFalse() {
        assertThatThrownBy(() -> service.saveProgress(
                assetId, new SaveAssetPlaybackProgressCommand(BigDecimal.valueOf(10), null)
        ))
                .isInstanceOf(InvalidPlaybackProgressException.class)
                .hasMessage("completed is required");

        verifyNoInteractions(assetQueries, progressStore, progressTransaction);
    }

    @Test
    void bothCompletionValuesAreAccepted() {
        authorize(uploadAsset(AssetStatus.SEARCHABLE));
        when(currentUser.getCurrentUserId()).thenReturn(CURRENT_USER);
        whenUpsertEchoes();

        assertThat(service.saveProgress(assetId, command(10, false)).completed()).isFalse();
        assertThat(service.saveProgress(assetId, command(10, true)).completed()).isTrue();
    }

    // --------------------------------------------------------------- helpers

    private void authorize(Asset asset) {
        when(assetQueries.loadAuthorizedAsset(assetId)).thenReturn(asset);
    }

    private void whenUpsertEchoes() {
        when(progressTransaction.upsert(any(), anyString(), anyLong(), anyBoolean(), any(Instant.class)))
                .thenAnswer(invocation -> new AssetPlaybackProgressSnapshot(
                        invocation.getArgument(2, Long.class),
                        invocation.getArgument(3, Boolean.class),
                        invocation.getArgument(4, Instant.class)
                ));
    }

    private SaveAssetPlaybackProgressCommand command(long positionMs, boolean completed) {
        return new SaveAssetPlaybackProgressCommand(BigDecimal.valueOf(positionMs), completed);
    }

    private Asset uploadAsset(AssetStatus status) {
        return Asset.uploaded(
                assetId, "lecture.mp4", "Uploaded lecture", status, UUID.randomUUID(),
                "workspace-media", "objects/lecture.mp4", "video/mp4", 42L, "etag-1"
        );
    }
}
