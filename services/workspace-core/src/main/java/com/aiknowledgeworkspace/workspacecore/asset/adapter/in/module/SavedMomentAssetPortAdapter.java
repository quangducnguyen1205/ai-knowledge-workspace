package com.aiknowledgeworkspace.workspacecore.asset.adapter.in.module;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetCanonicalMoment;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.AssetCanonicalMomentTarget;
import com.aiknowledgeworkspace.workspacecore.asset.application.service.AssetTranscriptQueryService;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentAssetPort;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentCanonicalMoment;
import com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset.SavedMomentTarget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Asset-side implementation of the Saved Moment outbound port. Saved Moment therefore never sees
 * an Asset repository or JPA entity, and authorization stays with the Asset module.
 */
@Component
public class SavedMomentAssetPortAdapter implements SavedMomentAssetPort {

    private static final int MAX_TARGETS = 100;

    private final AssetTranscriptQueryService transcriptQueryService;

    public SavedMomentAssetPortAdapter(AssetTranscriptQueryService transcriptQueryService) {
        this.transcriptQueryService = transcriptQueryService;
    }

    @Override
    public Optional<SavedMomentCanonicalMoment> findAuthorizedMoment(UUID assetId, String transcriptRowId) {
        return findAuthorizedMoments(List.of(new SavedMomentTarget(assetId, transcriptRowId)))
                .stream()
                .findFirst();
    }

    @Override
    public List<SavedMomentCanonicalMoment> findAuthorizedMoments(List<SavedMomentTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        List<AssetCanonicalMomentTarget> assetTargets = targets.stream()
                .filter(target -> target != null && target.assetId() != null)
                .limit(MAX_TARGETS)
                .map(target -> new AssetCanonicalMomentTarget(target.assetId(), target.transcriptRowId()))
                .toList();
        return transcriptQueryService.findAuthorizedCanonicalMoments(assetTargets).stream()
                .map(this::toSavedMomentMoment)
                .toList();
    }

    private SavedMomentCanonicalMoment toSavedMomentMoment(AssetCanonicalMoment moment) {
        return new SavedMomentCanonicalMoment(
                moment.assetId(),
                moment.workspaceId(),
                moment.assetTitle(),
                moment.sourceType() == null ? null : moment.sourceType().name(),
                moment.transcriptRowId(),
                moment.segmentIndex(),
                moment.startMs(),
                moment.endMs(),
                moment.text()
        );
    }
}
