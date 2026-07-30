package com.aiknowledgeworkspace.workspacecore.savedmoment.application.port.out.asset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bounded Asset facts this module needs. The Asset module implements it, so Saved Moment never
 * reaches into another module's repositories or JPA entities.
 */
public interface SavedMomentAssetPort {

    /**
     * Resolves one authorized Asset and one canonical transcript row. Empty means the Asset is
     * missing, not owned by the current user, or has no such canonical row.
     */
    Optional<SavedMomentCanonicalMoment> findAuthorizedMoment(UUID assetId, String transcriptRowId);

    /**
     * Resolves many canonical moments at once, grouped by Asset so the list never becomes an
     * N+1 query. Targets whose Asset or canonical row no longer exists are simply absent.
     */
    List<SavedMomentCanonicalMoment> findAuthorizedMoments(List<SavedMomentTarget> targets);
}
