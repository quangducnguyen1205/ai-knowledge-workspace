package com.aiknowledgeworkspace.workspacecore.search.indexing.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class IndexingFailureDiagnostic {

    private static final int MAX_DIAGNOSTIC_LENGTH = 1024;
    private static final int MAX_SEGMENT_INDEXES = 16;

    private IndexingFailureDiagnostic() {
    }

    public static String from(
            List<RowMetadata> transcriptRows,
            Category category,
            FailureStage failureStage,
            String exceptionType
    ) {
        RowSummary rowSummary = summarize(transcriptRows);
        String diagnostic = String.join(";",
                "diagnosticVersion=1",
                "category=" + category.name(),
                "failureStage=" + failureStage.value,
                "exception=" + safeExceptionClass(exceptionType),
                "usableRows=" + transcriptRows.size(),
                "blankRowsAfterFilter=" + rowSummary.blankRowsAfterFilter(),
                "segmentIndexes=" + segmentIndexSummary(rowSummary.segmentIndexCounts()),
                "missingSegmentIndexes=" + rowSummary.missingSegmentIndexes(),
                "textLengthMin=" + textLengthValue(rowSummary.textLengthMin()),
                "textLengthMax=" + textLengthValue(rowSummary.textLengthMax()),
                "textLengthBuckets=" + textLengthBuckets(rowSummary.textLengthBuckets())
        );
        if (diagnostic.length() <= MAX_DIAGNOSTIC_LENGTH) {
            return diagnostic;
        }
        return diagnostic.substring(0, MAX_DIAGNOSTIC_LENGTH);
    }

    private static String safeExceptionClass(String exceptionType) {
        return exceptionType == null || exceptionType.isBlank() ? "none" : exceptionType;
    }

    private static RowSummary summarize(List<RowMetadata> transcriptRows) {
        long blankRowsAfterFilter = 0;
        long missingSegmentIndexes = 0;
        Integer textLengthMin = null;
        Integer textLengthMax = null;
        Map<Integer, Long> segmentIndexCounts = new TreeMap<>(Comparator.naturalOrder());
        TextLengthBuckets textLengthBuckets = new TextLengthBuckets();

        for (RowMetadata row : transcriptRows) {
            Integer segmentIndex = row.segmentIndex();
            if (segmentIndex == null) {
                missingSegmentIndexes++;
            } else {
                segmentIndexCounts.merge(segmentIndex, 1L, Long::sum);
            }

            if (!row.hasText()) {
                blankRowsAfterFilter++;
            }
            Integer textLength = row.textLength();
            textLengthBuckets.record(textLength);
            if (textLength != null) {
                textLengthMin = textLengthMin == null ? textLength : Math.min(textLengthMin, textLength);
                textLengthMax = textLengthMax == null ? textLength : Math.max(textLengthMax, textLength);
            }
        }

        return new RowSummary(
                blankRowsAfterFilter,
                segmentIndexCounts,
                missingSegmentIndexes,
                textLengthMin,
                textLengthMax,
                textLengthBuckets
        );
    }

    private static String segmentIndexSummary(Map<Integer, Long> segmentIndexCounts) {
        if (segmentIndexCounts.isEmpty()) {
            return "none";
        }

        String summary = segmentIndexCounts.keySet().stream()
                .limit(MAX_SEGMENT_INDEXES)
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
        int overflowCount = segmentIndexCounts.size() - MAX_SEGMENT_INDEXES;
        if (overflowCount > 0) {
            summary = summary + ",...+" + overflowCount;
        }
        return summary;
    }

    private static String textLengthValue(Integer value) {
        return value == null ? "none" : String.valueOf(value);
    }

    private static String textLengthBuckets(TextLengthBuckets buckets) {
        return "null:" + buckets.nullCount
                + ",0:" + buckets.zeroCount
                + ",1-80:" + buckets.shortCount
                + ",81-280:" + buckets.mediumCount
                + ",281-1000:" + buckets.longCount
                + ",1001+:" + buckets.veryLongCount;
    }

    private record RowSummary(
            long blankRowsAfterFilter,
            Map<Integer, Long> segmentIndexCounts,
            long missingSegmentIndexes,
            Integer textLengthMin,
            Integer textLengthMax,
            TextLengthBuckets textLengthBuckets
    ) {
    }

    public record RowMetadata(
            Integer segmentIndex,
            boolean hasText,
            Integer textLength
    ) {
    }

    private static final class TextLengthBuckets {

        private long nullCount;
        private long zeroCount;
        private long shortCount;
        private long mediumCount;
        private long longCount;
        private long veryLongCount;

        private void record(Integer textLength) {
            if (textLength == null) {
                nullCount++;
                return;
            }
            if (textLength == 0) {
                zeroCount++;
            } else if (textLength <= 80) {
                shortCount++;
            } else if (textLength <= 280) {
                mediumCount++;
            } else if (textLength <= 1000) {
                longCount++;
            } else {
                veryLongCount++;
            }
        }
    }

    public enum Category {
        INDEXING_SOURCE_INVALID,
        ELASTICSEARCH_BULK_REJECTED,
        ELASTICSEARCH_TRANSPORT_FAILURE,
        ELASTICSEARCH_RESPONSE_INVALID,
        INDEXING_UNEXPECTED_FAILURE
    }

    public enum FailureStage {
        BEFORE_BULK("before_bulk"),
        TRANSPORT("transport"),
        BULK_RESPONSE("bulk_response"),
        AFTER_BULK("after_bulk"),
        UNEXPECTED("unexpected");

        private final String value;

        FailureStage(String value) {
            this.value = value;
        }
    }
}
