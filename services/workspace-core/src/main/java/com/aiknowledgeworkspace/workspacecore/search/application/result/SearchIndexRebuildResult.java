package com.aiknowledgeworkspace.workspacecore.search.application.result;

/**
 * Outcome of one projection rebuild run.
 *
 * <p>{@code skipped} covers assets a rebuild deliberately leaves alone: no usable transcript rows,
 * or an attempt already in flight for the same snapshot. {@code superseded} covers assets whose
 * transcript moved on mid-run, which the newer job owns instead.
 */
public record SearchIndexRebuildResult(
        int eligible,
        int indexed,
        int superseded,
        int skipped,
        int failed
) {

    public boolean hasFailures() {
        return failed > 0;
    }

    public String summary() {
        return "eligible=%d indexed=%d superseded=%d skipped=%d failed=%d"
                .formatted(eligible, indexed, superseded, skipped, failed);
    }
}
