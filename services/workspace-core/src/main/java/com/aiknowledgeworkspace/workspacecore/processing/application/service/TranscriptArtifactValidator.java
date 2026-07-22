package com.aiknowledgeworkspace.workspacecore.processing.application.service;

import com.aiknowledgeworkspace.workspacecore.processing.api.ProcessingTranscriptRow;

import com.aiknowledgeworkspace.workspacecore.processing.application.model.ProcessingResultEventApplyException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TranscriptArtifactValidator {

    private static final int MAX_TRANSCRIPT_ROWS = 20_000;
    private static final int MAX_TRANSCRIPT_ROW_ID_LENGTH = 255;
    private static final int MAX_VIDEO_ID_LENGTH = 128;
    private static final int MAX_TEXT_LENGTH = 20_000;
    private static final int MAX_CREATED_AT_LENGTH = 64;

    public List<ProcessingTranscriptRow> validate(List<ProcessingTranscriptRow> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new ProcessingResultEventApplyException("Transcript artifact rows were empty");
        }
        if (rows.size() > MAX_TRANSCRIPT_ROWS) {
            throw new ProcessingResultEventApplyException("Transcript artifact row count exceeded the safe limit");
        }
        Set<Integer> segmentIndexes = new HashSet<>();
        Set<String> transcriptRowIds = new HashSet<>();
        Integer previousSegmentIndex = null;
        for (ProcessingTranscriptRow row : rows) {
            validateRow(row, previousSegmentIndex, segmentIndexes, transcriptRowIds);
            previousSegmentIndex = row.segmentIndex();
        }
        return List.copyOf(rows);
    }

    private void validateRow(
            ProcessingTranscriptRow row,
            Integer previousSegmentIndex,
            Set<Integer> segmentIndexes,
            Set<String> transcriptRowIds
    ) {
        if (row == null) {
            throw new ProcessingResultEventApplyException("Transcript artifact contained a null row");
        }
        if (row.segmentIndex() == null || row.segmentIndex() < 0) {
            throw new ProcessingResultEventApplyException("Transcript artifact row segmentIndex is required");
        }
        if (previousSegmentIndex != null && row.segmentIndex() <= previousSegmentIndex) {
            throw new ProcessingResultEventApplyException("Transcript artifact rows must be strictly ordered");
        }
        if (!segmentIndexes.add(row.segmentIndex())) {
            throw new ProcessingResultEventApplyException("Transcript artifact row segmentIndex was duplicated");
        }
        if (!StringUtils.hasText(row.videoId()) || row.videoId().length() > MAX_VIDEO_ID_LENGTH) {
            throw new ProcessingResultEventApplyException("Transcript artifact row videoId was invalid");
        }
        if (StringUtils.hasText(row.id())
                && (row.id().length() > MAX_TRANSCRIPT_ROW_ID_LENGTH || !transcriptRowIds.add(row.id()))) {
            throw new ProcessingResultEventApplyException("Transcript artifact row id was invalid or duplicated");
        }
        if (!StringUtils.hasText(row.text()) || row.text().length() > MAX_TEXT_LENGTH) {
            throw new ProcessingResultEventApplyException("Transcript artifact row text was empty or too large");
        }
        validateTiming(row);
        if (!StringUtils.hasText(row.createdAt()) || row.createdAt().length() > MAX_CREATED_AT_LENGTH) {
            throw new ProcessingResultEventApplyException("Transcript artifact row createdAt was invalid");
        }
    }

    private void validateTiming(ProcessingTranscriptRow row) {
        if ((row.startMs() == null) != (row.endMs() == null)) {
            throw new ProcessingResultEventApplyException(
                    "Transcript artifact row timestamps must both be present or both be absent"
            );
        }
        if (row.startMs() != null && (row.startMs() < 0 || row.endMs() < row.startMs())) {
            throw new ProcessingResultEventApplyException("Transcript artifact row timestamps were invalid");
        }
    }
}
