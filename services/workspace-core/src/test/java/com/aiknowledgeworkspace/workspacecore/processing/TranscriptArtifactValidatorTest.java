package com.aiknowledgeworkspace.workspacecore.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingTranscriptRow;
import com.aiknowledgeworkspace.workspacecore.processing.application.model.ProcessingResultEventApplyException;
import com.aiknowledgeworkspace.workspacecore.processing.application.service.TranscriptArtifactValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptArtifactValidatorTest {

    private final TranscriptArtifactValidator validator = new TranscriptArtifactValidator();

    @Test
    void acceptsLegacyNullTimingAndCompleteNonNegativeTimingIncludingZero() {
        List<ProcessingTranscriptRow> rows = List.of(
                row("legacy", 0, null, null),
                row("timed", 1, 0L, 1250L)
        );

        assertThat(validator.validate(rows)).containsExactlyElementsOf(rows);
    }

    @Test
    void rejectsPartialNegativeOrBackwardTiming() {
        assertInvalid(row("partial", 0, 0L, null));
        assertInvalid(row("negative", 0, -1L, 0L));
        assertInvalid(row("backward", 0, 100L, 99L));
    }

    private void assertInvalid(ProcessingTranscriptRow row) {
        assertThatThrownBy(() -> validator.validate(List.of(row)))
                .isInstanceOf(ProcessingResultEventApplyException.class);
    }

    private ProcessingTranscriptRow row(String id, int segmentIndex, Long startMs, Long endMs) {
        return new ProcessingTranscriptRow(
                id, "video-1", segmentIndex, startMs, endMs, "text", "2026-07-22T00:00:00Z"
        );
    }
}
