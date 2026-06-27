package com.aiknowledgeworkspace.workspacecore.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.asset.AssetTranscriptRowView;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptSnapshotFingerprintServiceTest {

    private final TranscriptSnapshotFingerprintService fingerprintService = new TranscriptSnapshotFingerprintService();

    @Test
    void sameOrderedRowsProduceSameFingerprint() {
        List<AssetTranscriptRowView> rows = List.of(
                row(0, "first"),
                row(1, "second")
        );

        assertThat(fingerprintService.fingerprint(rows))
                .isEqualTo(fingerprintService.fingerprint(List.of(row(0, "first"), row(1, "second"))));
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

    private AssetTranscriptRowView row(int segmentIndex, String text) {
        return new AssetTranscriptRowView(
                "ignored-row-id-" + segmentIndex,
                "video-1",
                segmentIndex,
                text,
                "2026-06-22T00:00:00Z"
        );
    }
}
