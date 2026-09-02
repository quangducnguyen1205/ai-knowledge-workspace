package com.aiknowledgeworkspace.workspacecore.search.application.result;

/**
 * Outcome of one stale-indexing recovery pass.
 *
 * <p>{@code skipped} counts jobs another worker claimed first or that finished on their own between
 * the scan and the claim — an ordinary race, not a problem. {@code failed} counts jobs whose replay
 * threw, which leaves them claimed and stale again for a later pass.
 */
public record IndexingRecoveryResult(int eligible, int recovered, int skipped, int failed, boolean disabled) {

    public static IndexingRecoveryResult disabledResult() {
        return new IndexingRecoveryResult(0, 0, 0, 0, true);
    }
}
