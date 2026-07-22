package com.aiknowledgeworkspace.workspacecore.search;

import com.aiknowledgeworkspace.workspacecore.search.application.service.TranscriptSnapshotFingerprintService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.IndexingTranscriptRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptSnapshotFingerprintServiceTest {

    private final TranscriptSnapshotFingerprintService fingerprintService = new TranscriptSnapshotFingerprintService();

    @Test
    void sameOrderedRowsProduceSameFingerprint() {
        List<IndexingTranscriptRow> rows = List.of(
                row(0, "first"),
                row(1, "second")
        );

        assertThat(fingerprintService.fingerprint(rows))
                .isEqualTo(fingerprintService.fingerprint(List.of(row(0, "first"), row(1, "second"))));
    }

    @Test
    void legacyRowsKeepThePrePhaseOneGoldenFingerprint() {
        assertThat(fingerprintService.fingerprint(List.of(row(0, "first"), row(1, "second"))))
                .isEqualTo("4eadbca95e55585c1a3268fa19b6db8edf2f8e830dc83104aa07912e7b175315");
    }

    @Test
    void timingIsIncludedOnlyWhenPresentAndEachBoundaryChangesTheFingerprint() {
        String legacy = fingerprintService.fingerprint(List.of(row(0, "first")));
        String timed = fingerprintService.fingerprint(List.of(row(0, "first", 0L, 1000L)));

        assertThat(timed).isNotEqualTo(legacy);
        assertThat(fingerprintService.fingerprint(List.of(row(0, "first", 1L, 1000L))))
                .isNotEqualTo(timed);
        assertThat(fingerprintService.fingerprint(List.of(row(0, "first", 0L, 1001L))))
                .isNotEqualTo(timed);
    }

    @Test
    void partialTimingPairsAreRejectedInsteadOfBeingEncoded() {
        assertThatThrownBy(() -> fingerprintService.fingerprint(List.of(row(0, "first", 0L, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both be present or both be absent");
        assertThatThrownBy(() -> fingerprintService.fingerprint(List.of(row(0, "first", null, 1000L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both be present or both be absent");
    }

    @Test
    void textChangeChangesFingerprint() {
        assertThat(fingerprintService.fingerprint(List.of(row(0, "first"))))
                .isNotEqualTo(fingerprintService.fingerprint(List.of(row(0, "changed"))));
    }

    @Test
    void segmentIndexChangeChangesFingerprint() {
        assertThat(fingerprintService.fingerprint(List.of(row(0, "first"))))
                .isNotEqualTo(fingerprintService.fingerprint(List.of(row(1, "first"))));
    }

    @Test
    void rowCountChangeChangesFingerprint() {
        assertThat(fingerprintService.fingerprint(List.of(row(0, "first"))))
                .isNotEqualTo(fingerprintService.fingerprint(List.of(row(0, "first"), row(1, "second"))));
    }

    @Test
    void rowOrderChangeChangesFingerprint() {
        assertThat(fingerprintService.fingerprint(List.of(row(0, "first"), row(1, "second"))))
                .isNotEqualTo(fingerprintService.fingerprint(List.of(row(1, "second"), row(0, "first"))));
    }

    private IndexingTranscriptRow row(int segmentIndex, String text) {
        return row(segmentIndex, text, null, null);
    }

    private IndexingTranscriptRow row(int segmentIndex, String text, Long startMs, Long endMs) {
        return new IndexingTranscriptRow(
                "ignored-row-id-" + segmentIndex,
                "video-1",
                segmentIndex,
                startMs,
                endMs,
                text,
                "2026-06-22T00:00:00Z"
        );
    }
}
