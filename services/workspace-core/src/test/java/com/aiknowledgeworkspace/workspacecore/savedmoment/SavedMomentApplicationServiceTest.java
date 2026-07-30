package com.aiknowledgeworkspace.workspacecore.savedmoment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiknowledgeworkspace.workspacecore.identity.api.CurrentUserContext;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.command.SaveMomentCommand;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.InvalidSavedMomentRequestException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.SavedMomentNotFoundException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.SavedMomentTargetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.model.SavedMomentRecord;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.SavedMomentAlreadySavedException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentAssetPort;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentCanonicalMoment;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentTarget;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.result.SavedMomentListView;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.result.SavedMomentView;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccess;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

class SavedMomentApplicationServiceTest {

    private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_WORKSPACE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String USER_ID = "user-1";

    private final SavedMomentTransaction transaction = mock(SavedMomentTransaction.class);
    private final SavedMomentAssetPort assetPort = mock(SavedMomentAssetPort.class);
    private final WorkspaceAccessUseCase workspaceAccess = mock(WorkspaceAccessUseCase.class);
    private final CurrentUserContext currentUser = mock(CurrentUserContext.class);
    private final SavedMomentApplicationService service =
            new SavedMomentApplicationService(transaction, assetPort, workspaceAccess, currentUser);

    // ------------------------------------------------------------------ save

    @Test
    void firstSaveStoresCanonicalIdentityAndReturnsCurrentCanonicalData() {
        currentUserIs(USER_ID);
        canonicalMomentExists("row-1", 7, 1000L, 4000L, "Canonical text.");
        when(transaction.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SavedMomentView view = service.save(new SaveMomentCommand(ASSET_ID, "row-1"));

        ArgumentCaptor<SavedMomentRecord> captor = ArgumentCaptor.forClass(SavedMomentRecord.class);
        verify(transaction).insert(captor.capture());
        SavedMomentRecord stored = captor.getValue();
        assertThat(stored.userId()).isEqualTo(USER_ID);
        assertThat(stored.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(stored.assetId()).isEqualTo(ASSET_ID);
        assertThat(stored.transcriptRowId()).isEqualTo("row-1");
        assertThat(stored.savedAt()).isNotNull();

        assertThat(view.savedMomentId()).isEqualTo(stored.savedMomentId());
        assertThat(view.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(view.assetTitle()).isEqualTo("Lecture");
        assertThat(view.sourceType()).isEqualTo("UPLOAD");
        assertThat(view.segmentIndex()).isEqualTo(7);
        assertThat(view.startMs()).isEqualTo(1000L);
        assertThat(view.endMs()).isEqualTo(4000L);
        assertThat(view.text()).isEqualTo("Canonical text.");
    }

    @Test
    void repeatedSaveIsIdempotentAndReturnsTheExistingRecordWithoutASecondRow() {
        currentUserIs(USER_ID);
        canonicalMomentExists("row-1", 7, 1000L, 4000L, "Canonical text.");
        SavedMomentRecord existing = record(UUID.randomUUID(), USER_ID, "row-1", Instant.parse("2026-01-01T00:00:00Z"));
        when(transaction.insert(any()))
                .thenThrow(new SavedMomentAlreadySavedException(new IllegalStateException("unique")));
        when(transaction.find(USER_ID, ASSET_ID, "row-1")).thenReturn(Optional.of(existing));

        SavedMomentView view = service.save(new SaveMomentCommand(ASSET_ID, "row-1"));

        assertThat(view.savedMomentId()).isEqualTo(existing.savedMomentId());
        assertThat(view.savedAt()).isEqualTo(existing.savedAt());
        verify(transaction, times(1)).insert(any());
    }

    @Test
    void theDatabaseOwnsTheDuplicateBoundarySoTheServiceNeverReadsBeforeWriting() {
        currentUserIs(USER_ID);
        canonicalMomentExists("row-1", 7, 1000L, 4000L, "Canonical text.");
        when(transaction.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.save(new SaveMomentCommand(ASSET_ID, "row-1"));

        InOrder order = Mockito.inOrder(assetPort, transaction);
        order.verify(assetPort).findAuthorizedMoment(ASSET_ID, "row-1");
        order.verify(transaction).insert(any());
        verify(transaction, never()).find(anyString(), any(), anyString());
    }

    @Test
    void savingAForeignOrMissingAssetOrRowIsIndistinguishableAndNeverWrites() {
        currentUserIs(USER_ID);
        when(assetPort.findAuthorizedMoment(any(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(new SaveMomentCommand(ASSET_ID, "row-1")))
                .isInstanceOf(SavedMomentTargetNotFoundException.class);
        verify(transaction, never()).insert(any());
    }

    @Test
    void authorizationHappensBeforeAnyPersistenceWork() {
        currentUserIs(USER_ID);
        when(assetPort.findAuthorizedMoment(any(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(new SaveMomentCommand(ASSET_ID, "row-1")))
                .isInstanceOf(SavedMomentTargetNotFoundException.class);
        verifyNoInteractions(transaction);
    }

    @Test
    void saveRejectsAMissingAssetIdOrBlankTranscriptRowId() {
        assertThatThrownBy(() -> service.save(new SaveMomentCommand(null, "row-1")))
                .isInstanceOf(InvalidSavedMomentRequestException.class)
                .hasMessageContaining("assetId");
        assertThatThrownBy(() -> service.save(new SaveMomentCommand(ASSET_ID, "   ")))
                .isInstanceOf(InvalidSavedMomentRequestException.class)
                .hasMessageContaining("transcriptRowId");
        assertThatThrownBy(() -> service.save(new SaveMomentCommand(ASSET_ID, null)))
                .isInstanceOf(InvalidSavedMomentRequestException.class);
        assertThatThrownBy(() -> service.save(null))
                .isInstanceOf(InvalidSavedMomentRequestException.class);
        assertThatThrownBy(() -> service.save(new SaveMomentCommand(ASSET_ID, "r".repeat(256))))
                .isInstanceOf(InvalidSavedMomentRequestException.class);
        verifyNoInteractions(transaction);
        verifyNoInteractions(assetPort);
    }

    @Test
    void theCanonicalRowIdIsAuthoritativeAndIsStoredExactlyAsResolved() {
        currentUserIs(USER_ID);
        when(assetPort.findAuthorizedMoment(ASSET_ID, "row-1")).thenReturn(Optional.of(
                new SavedMomentCanonicalMoment(
                        ASSET_ID, WORKSPACE_ID, "Lecture", "YOUTUBE", "row-1", 7, null, null, "Text."
                )));
        when(transaction.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SavedMomentView view = service.save(new SaveMomentCommand(ASSET_ID, "  row-1  "));

        assertThat(view.transcriptRowId()).isEqualTo("row-1");
        assertThat(view.sourceType()).isEqualTo("YOUTUBE");
        assertThat(view.startMs()).isNull();
        assertThat(view.endMs()).isNull();
    }

    // ------------------------------------------------------------------ list

    @Test
    void listReturnsOnlyCurrentUserAndWorkspaceInStoredNewestFirstOrder() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        SavedMomentRecord newest = record(uuid(3), USER_ID, "row-c", Instant.parse("2026-03-01T00:00:00Z"));
        SavedMomentRecord middle = record(uuid(2), USER_ID, "row-b", Instant.parse("2026-02-01T00:00:00Z"));
        SavedMomentRecord oldest = record(uuid(1), USER_ID, "row-a", Instant.parse("2026-01-01T00:00:00Z"));
        when(transaction.findRecent(USER_ID, WORKSPACE_ID, 100)).thenReturn(List.of(newest, middle, oldest));
        canonicalMomentsExist("row-a", "row-b", "row-c");

        SavedMomentListView view = service.listForWorkspace(WORKSPACE_ID);

        assertThat(view.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(view.savedMomentCount()).isEqualTo(3);
        assertThat(view.maxItems()).isEqualTo(100);
        assertThat(view.items()).extracting(SavedMomentView::transcriptRowId)
                .containsExactly("row-c", "row-b", "row-a");
        verify(transaction).findRecent(USER_ID, WORKSPACE_ID, 100);
    }

    @Test
    void listAsksTheStoreForTheServerOwnedMaximum() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(transaction.findRecent(anyString(), any(), anyInt())).thenReturn(List.of());

        service.listForWorkspace(WORKSPACE_ID);

        verify(transaction).findRecent(USER_ID, WORKSPACE_ID, SavedMomentApplicationService.MAX_ITEMS_PER_WORKSPACE);
        assertThat(SavedMomentApplicationService.MAX_ITEMS_PER_WORKSPACE).isEqualTo(100);
    }

    @Test
    void aSavedMomentWhoseCanonicalRowDisappearedIsNeverReturnedAsNavigable() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        SavedMomentRecord present = record(uuid(1), USER_ID, "row-a", Instant.parse("2026-02-01T00:00:00Z"));
        SavedMomentRecord removed = record(uuid(2), USER_ID, "row-gone", Instant.parse("2026-01-01T00:00:00Z"));
        when(transaction.findRecent(USER_ID, WORKSPACE_ID, 100)).thenReturn(List.of(present, removed));
        canonicalMomentsExist("row-a");

        SavedMomentListView view = service.listForWorkspace(WORKSPACE_ID);

        assertThat(view.items()).extracting(SavedMomentView::transcriptRowId).containsExactly("row-a");
        assertThat(view.savedMomentCount()).isEqualTo(1);
    }

    @Test
    void listNeverWritesWhenACanonicalRowIsMissing() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(transaction.findRecent(USER_ID, WORKSPACE_ID, 100))
                .thenReturn(List.of(record(uuid(1), USER_ID, "row-gone", Instant.now())));
        when(assetPort.findAuthorizedMoments(any())).thenReturn(List.of());

        service.listForWorkspace(WORKSPACE_ID);

        verify(transaction, never()).delete(any());
        verify(transaction, never()).insert(any());
    }

    @Test
    void listDropsARecordWhoseCanonicalAssetMovedOutOfTheResolvedWorkspace() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(transaction.findRecent(USER_ID, WORKSPACE_ID, 100))
                .thenReturn(List.of(record(uuid(1), USER_ID, "row-a", Instant.now())));
        when(assetPort.findAuthorizedMoments(any())).thenReturn(List.of(
                new SavedMomentCanonicalMoment(
                        ASSET_ID, OTHER_WORKSPACE_ID, "Lecture", "UPLOAD", "row-a", 1, 0L, 1L, "Text."
                )));

        assertThat(service.listForWorkspace(WORKSPACE_ID).items()).isEmpty();
    }

    @Test
    void listResolvesCanonicalDataInOneBatchRatherThanOnceForEachSavedMoment() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        List<SavedMomentRecord> records = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            records.add(record(uuid(index), USER_ID, "row-" + index, Instant.parse("2026-01-01T00:00:00Z")));
        }
        when(transaction.findRecent(USER_ID, WORKSPACE_ID, 100)).thenReturn(records);
        when(assetPort.findAuthorizedMoments(any())).thenReturn(List.of());

        service.listForWorkspace(WORKSPACE_ID);

        verify(assetPort, times(1)).findAuthorizedMoments(any());
        verify(assetPort, never()).findAuthorizedMoment(any(), anyString());
    }

    @Test
    void listReflectsCurrentCanonicalTextAndTimingRatherThanAStoredSnapshot() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(transaction.findRecent(USER_ID, WORKSPACE_ID, 100))
                .thenReturn(List.of(record(uuid(1), USER_ID, "row-a", Instant.now())));
        when(assetPort.findAuthorizedMoments(any())).thenReturn(List.of(
                new SavedMomentCanonicalMoment(
                        ASSET_ID, WORKSPACE_ID, "Renamed lecture", "UPLOAD", "row-a", 9, 9000L, 9500L, "Edited text."
                )));

        assertThat(service.listForWorkspace(WORKSPACE_ID).items()).singleElement().satisfies(item -> {
            assertThat(item.assetTitle()).isEqualTo("Renamed lecture");
            assertThat(item.text()).isEqualTo("Edited text.");
            assertThat(item.segmentIndex()).isEqualTo(9);
            assertThat(item.startMs()).isEqualTo(9000L);
        });
    }

    @Test
    void listWithoutARequestedWorkspaceUsesTheResolvedDefaultWorkspace() {
        currentUserIs(USER_ID);
        when(workspaceAccess.resolveWorkspaceOrDefault(null))
                .thenReturn(new WorkspaceAccess(WORKSPACE_ID, USER_ID));
        when(transaction.findRecent(USER_ID, WORKSPACE_ID, 100)).thenReturn(List.of());

        assertThat(service.listForWorkspace(null).workspaceId()).isEqualTo(WORKSPACE_ID);
    }

    @Test
    void anEmptyListSkipsCanonicalResolutionEntirely() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(transaction.findRecent(USER_ID, WORKSPACE_ID, 100)).thenReturn(List.of());

        SavedMomentListView view = service.listForWorkspace(WORKSPACE_ID);

        assertThat(view.items()).isEmpty();
        assertThat(view.savedMomentCount()).isZero();
        verifyNoInteractions(assetPort);
    }

    // ---------------------------------------------------------------- remove

    @Test
    void removeDeletesOnlyARecordOwnedByTheCurrentUser() {
        currentUserIs(USER_ID);
        UUID savedMomentId = uuid(5);
        when(transaction.findOwned(savedMomentId, USER_ID))
                .thenReturn(Optional.of(record(savedMomentId, USER_ID, "row-a", Instant.now())));

        service.remove(savedMomentId);

        verify(transaction).delete(savedMomentId);
    }

    @Test
    void removingAForeignOrUnknownSavedMomentIsIndistinguishableAndDeletesNothing() {
        currentUserIs(USER_ID);
        UUID savedMomentId = uuid(5);
        when(transaction.findOwned(eq(savedMomentId), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(savedMomentId))
                .isInstanceOf(SavedMomentNotFoundException.class);
        verify(transaction, never()).delete(any());
    }

    @Test
    void removeRejectsAMissingIdentifier() {
        assertThatThrownBy(() -> service.remove(null))
                .isInstanceOf(InvalidSavedMomentRequestException.class);
        verifyNoInteractions(transaction);
    }

    // --------------------------------------------------------------- cleanup

    @Test
    void assetCleanupRemovesEverySavedMomentOfThatAsset() {
        service.deleteForAsset(ASSET_ID);

        verify(transaction).deleteForAsset(ASSET_ID);
    }

    @Test
    void assetCleanupIgnoresAMissingAssetIdentifier() {
        service.deleteForAsset(null);

        verifyNoInteractions(transaction);
    }

    // --------------------------------------------------------------- helpers

    private void currentUserIs(String userId) {
        when(currentUser.getCurrentUserId()).thenReturn(userId);
    }

    private void workspaceResolvesTo(UUID workspaceId) {
        when(workspaceAccess.resolveWorkspaceOrDefault(workspaceId))
                .thenReturn(new WorkspaceAccess(workspaceId, USER_ID));
    }

    private void canonicalMomentExists(
            String transcriptRowId, Integer segmentIndex, Long startMs, Long endMs, String text
    ) {
        when(assetPort.findAuthorizedMoment(ASSET_ID, transcriptRowId)).thenReturn(Optional.of(
                new SavedMomentCanonicalMoment(
                        ASSET_ID, WORKSPACE_ID, "Lecture", "UPLOAD",
                        transcriptRowId, segmentIndex, startMs, endMs, text
                )));
    }

    private void canonicalMomentsExist(String... transcriptRowIds) {
        List<SavedMomentCanonicalMoment> moments = new ArrayList<>();
        for (String transcriptRowId : transcriptRowIds) {
            moments.add(new SavedMomentCanonicalMoment(
                    ASSET_ID, WORKSPACE_ID, "Lecture", "UPLOAD", transcriptRowId, 1, 0L, 1000L, "Text."
            ));
        }
        when(assetPort.findAuthorizedMoments(any())).thenReturn(moments);
    }

    private SavedMomentRecord record(UUID savedMomentId, String userId, String transcriptRowId, Instant savedAt) {
        return new SavedMomentRecord(savedMomentId, userId, WORKSPACE_ID, ASSET_ID, transcriptRowId, savedAt);
    }

    private static UUID uuid(int seed) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", seed));
    }

    @Test
    void listTargetsAreDeduplicatedBeforeCanonicalResolution() {
        currentUserIs(USER_ID);
        workspaceResolvesTo(WORKSPACE_ID);
        when(transaction.findRecent(USER_ID, WORKSPACE_ID, 100)).thenReturn(List.of(
                record(uuid(1), USER_ID, "row-a", Instant.parse("2026-02-01T00:00:00Z")),
                record(uuid(2), USER_ID, "row-a", Instant.parse("2026-01-01T00:00:00Z"))
        ));
        canonicalMomentsExist("row-a");

        ArgumentCaptor<List<SavedMomentTarget>> captor = ArgumentCaptor.forClass(List.class);
        service.listForWorkspace(WORKSPACE_ID);

        verify(assetPort).findAuthorizedMoments(captor.capture());
        assertThat(captor.getValue()).containsExactly(new SavedMomentTarget(ASSET_ID, "row-a"));
    }
}
