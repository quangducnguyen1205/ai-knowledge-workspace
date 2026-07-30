package com.aiknowledgeworkspace.workspacecore.savedmoment.application.service;

import com.aiknowledgeworkspace.workspacecore.identity.api.CurrentUserContext;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.command.SaveMomentCommand;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.InvalidSavedMomentRequestException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.SavedMomentNotFoundException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.exception.SavedMomentTargetNotFoundException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.model.SavedMomentRecord;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.in.SavedMomentAssetCleanupUseCase;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.in.SavedMomentUseCase;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.SavedMomentAlreadySavedException;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentAssetPort;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentCanonicalMoment;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentTarget;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.result.SavedMomentListView;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.result.SavedMomentView;
import com.aiknowledgeworkspace.workspacecore.workspace.api.WorkspaceAccessUseCase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A saved moment stores canonical identity only. Presentation data is resolved from current Asset
 * state on every read, so transcript edits are reflected and a removed row can never be returned
 * as a navigable link.
 */
@Service
class SavedMomentApplicationService implements SavedMomentUseCase, SavedMomentAssetCleanupUseCase {

    static final int MAX_ITEMS_PER_WORKSPACE = 100;
    private static final int MAX_TRANSCRIPT_ROW_ID_LENGTH = 255;

    private final SavedMomentTransaction savedMomentTransaction;
    private final SavedMomentAssetPort assetPort;
    private final WorkspaceAccessUseCase workspaceAccess;
    private final CurrentUserContext currentUser;

    SavedMomentApplicationService(
            SavedMomentTransaction savedMomentTransaction,
            SavedMomentAssetPort assetPort,
            WorkspaceAccessUseCase workspaceAccess,
            CurrentUserContext currentUser
    ) {
        this.savedMomentTransaction = savedMomentTransaction;
        this.assetPort = assetPort;
        this.workspaceAccess = workspaceAccess;
        this.currentUser = currentUser;
    }

    @Override
    public SavedMomentView save(SaveMomentCommand command) {
        UUID assetId = validateAssetId(command == null ? null : command.assetId());
        String transcriptRowId = validateTranscriptRowId(command == null ? null : command.transcriptRowId());

        SavedMomentCanonicalMoment moment = assetPort.findAuthorizedMoment(assetId, transcriptRowId)
                .orElseThrow(SavedMomentTargetNotFoundException::new);

        String userId = currentUser.getCurrentUserId();
        SavedMomentRecord candidate = new SavedMomentRecord(
                UUID.randomUUID(),
                userId,
                moment.workspaceId(),
                moment.assetId(),
                moment.transcriptRowId(),
                Instant.now()
        );

        SavedMomentRecord stored;
        try {
            stored = savedMomentTransaction.insert(candidate);
        } catch (SavedMomentAlreadySavedException alreadySaved) {
            stored = savedMomentTransaction.find(userId, moment.assetId(), moment.transcriptRowId())
                    .orElseThrow(SavedMomentTargetNotFoundException::new);
        }
        return toView(stored, moment);
    }

    @Override
    public SavedMomentListView listForWorkspace(UUID requestedWorkspaceId) {
        UUID workspaceId = workspaceAccess.resolveWorkspaceOrDefault(requestedWorkspaceId).workspaceId();
        List<SavedMomentRecord> records = savedMomentTransaction.findRecent(
                currentUser.getCurrentUserId(), workspaceId, MAX_ITEMS_PER_WORKSPACE
        );
        if (records.isEmpty()) {
            return new SavedMomentListView(workspaceId, 0, MAX_ITEMS_PER_WORKSPACE, List.of());
        }

        Map<SavedMomentTarget, SavedMomentCanonicalMoment> canonicalByTarget = resolveCanonical(records);
        List<SavedMomentView> items = new ArrayList<>(records.size());
        for (SavedMomentRecord record : records) {
            SavedMomentCanonicalMoment moment = canonicalByTarget.get(
                    new SavedMomentTarget(record.assetId(), record.transcriptRowId())
            );
            if (moment == null || !workspaceId.equals(moment.workspaceId())) {
                continue;
            }
            items.add(toView(record, moment));
        }
        return new SavedMomentListView(workspaceId, items.size(), MAX_ITEMS_PER_WORKSPACE, List.copyOf(items));
    }

    @Override
    public void remove(UUID savedMomentId) {
        if (savedMomentId == null) {
            throw new InvalidSavedMomentRequestException("savedMomentId is required");
        }
        SavedMomentRecord record = savedMomentTransaction
                .findOwned(savedMomentId, currentUser.getCurrentUserId())
                .orElseThrow(SavedMomentNotFoundException::new);
        savedMomentTransaction.delete(record.savedMomentId());
    }

    @Override
    public void deleteForAsset(UUID assetId) {
        if (assetId != null) {
            savedMomentTransaction.deleteForAsset(assetId);
        }
    }

    private Map<SavedMomentTarget, SavedMomentCanonicalMoment> resolveCanonical(List<SavedMomentRecord> records) {
        List<SavedMomentTarget> targets = records.stream()
                .map(record -> new SavedMomentTarget(record.assetId(), record.transcriptRowId()))
                .distinct()
                .toList();
        Map<SavedMomentTarget, SavedMomentCanonicalMoment> canonicalByTarget = new LinkedHashMap<>();
        for (SavedMomentCanonicalMoment moment : assetPort.findAuthorizedMoments(targets)) {
            if (moment != null) {
                canonicalByTarget.putIfAbsent(
                        new SavedMomentTarget(moment.assetId(), moment.transcriptRowId()), moment
                );
            }
        }
        return canonicalByTarget;
    }

    private SavedMomentView toView(SavedMomentRecord record, SavedMomentCanonicalMoment moment) {
        return new SavedMomentView(
                record.savedMomentId(),
                moment.workspaceId(),
                moment.assetId(),
                moment.assetTitle(),
                moment.sourceType(),
                moment.transcriptRowId(),
                moment.segmentIndex(),
                moment.startMs(),
                moment.endMs(),
                moment.text(),
                record.savedAt()
        );
    }

    private UUID validateAssetId(UUID assetId) {
        if (assetId == null) {
            throw new InvalidSavedMomentRequestException("assetId is required");
        }
        return assetId;
    }

    private String validateTranscriptRowId(String transcriptRowId) {
        if (transcriptRowId == null || transcriptRowId.isBlank()) {
            throw new InvalidSavedMomentRequestException("transcriptRowId is required");
        }
        String normalized = transcriptRowId.trim();
        if (normalized.length() > MAX_TRANSCRIPT_ROW_ID_LENGTH) {
            throw new InvalidSavedMomentRequestException(
                    "transcriptRowId must be at most " + MAX_TRANSCRIPT_ROW_ID_LENGTH + " characters"
            );
        }
        return normalized;
    }
}
