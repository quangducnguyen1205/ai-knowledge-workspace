package com.aiknowledgeworkspace.workspacecore.search;

import java.util.Comparator;
import java.util.List;
import org.springframework.util.StringUtils;

final class IndexingFailureDiagnostic {

    private static final int MAX_DIAGNOSTIC_LENGTH = 1024;
    private static final int MAX_SEGMENT_INDEXES = 16;

    private IndexingFailureDiagnostic() {
    }

    static String from(
            List<TranscriptSearchIndexClient.TranscriptIndexOperation> operations,
            Category category,
            FailureStage failureStage,
            RuntimeException exception
    ) {
        List<TranscriptIndexDocument> documents = operations.stream()
                .map(TranscriptSearchIndexClient.TranscriptIndexOperation::document)
                .filter(document -> document != null)
                .toList();
        String diagnostic = String.join(";",
                "diagnosticVersion=1",
                "category=" + category.name(),
                "failureStage=" + failureStage.value,
                "exception=" + safeExceptionClass(exception),
                "usableRows=" + documents.size(),
                "blankRowsAfterFilter=" + blankRowsAfterFilter(documents),
                "segmentIndexes=" + segmentIndexSummary(documents),
                "missingSegmentIndexes=" + missingSegmentIndexCount(documents),
                "textLengthMin=" + textLengthMin(documents),
                "textLengthMax=" + textLengthMax(documents),
                "textLengthBuckets=" + textLengthBuckets(documents)
        );
        if (diagnostic.length() <= MAX_DIAGNOSTIC_LENGTH) {
            return diagnostic;
        }
        return diagnostic.substring(0, MAX_DIAGNOSTIC_LENGTH);
    }

    static String sourceInvalid() {
        return from(List.of(), Category.INDEXING_SOURCE_INVALID, FailureStage.BEFORE_BULK, null);
    }

    private static String safeExceptionClass(RuntimeException exception) {
        return exception == null ? "none" : exception.getClass().getSimpleName();
    }

    private static long blankRowsAfterFilter(List<TranscriptIndexDocument> documents) {
        return documents.stream()
                .filter(document -> !StringUtils.hasText(document.text()))
                .count();
    }

    private static String segmentIndexSummary(List<TranscriptIndexDocument> documents) {
        List<Integer> segmentIndexes = documents.stream()
                .map(TranscriptIndexDocument::segmentIndex)
                .filter(segmentIndex -> segmentIndex != null)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (segmentIndexes.isEmpty()) {
            return "none";
        }

        String summary = segmentIndexes.stream()
                .limit(MAX_SEGMENT_INDEXES)
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
        int overflowCount = segmentIndexes.size() - MAX_SEGMENT_INDEXES;
        if (overflowCount > 0) {
            summary = summary + ",...+" + overflowCount;
        }
        return summary;
    }

    private static long missingSegmentIndexCount(List<TranscriptIndexDocument> documents) {
        return documents.stream()
                .filter(document -> document.segmentIndex() == null)
                .count();
    }

    private static String textLengthMin(List<TranscriptIndexDocument> documents) {
        return documents.stream()
                .map(TranscriptIndexDocument::text)
                .filter(text -> text != null)
                .mapToInt(String::length)
                .min()
                .stream()
                .mapToObj(String::valueOf)
                .findFirst()
                .orElse("none");
    }

    private static String textLengthMax(List<TranscriptIndexDocument> documents) {
        return documents.stream()
                .map(TranscriptIndexDocument::text)
                .filter(text -> text != null)
                .mapToInt(String::length)
                .max()
                .stream()
                .mapToObj(String::valueOf)
                .findFirst()
                .orElse("none");
    }

    private static String textLengthBuckets(List<TranscriptIndexDocument> documents) {
        long nullCount = 0;
        long zeroCount = 0;
        long shortCount = 0;
        long mediumCount = 0;
        long longCount = 0;
        long veryLongCount = 0;

        for (TranscriptIndexDocument document : documents) {
            String text = document.text();
            if (text == null) {
                nullCount++;
                continue;
            }
            int length = text.length();
            if (length == 0) {
                zeroCount++;
            } else if (length <= 80) {
                shortCount++;
            } else if (length <= 280) {
                mediumCount++;
            } else if (length <= 1000) {
                longCount++;
            } else {
                veryLongCount++;
            }
        }

        return "null:" + nullCount
                + ",0:" + zeroCount
                + ",1-80:" + shortCount
                + ",81-280:" + mediumCount
                + ",281-1000:" + longCount
                + ",1001+:" + veryLongCount;
    }

    enum Category {
        INDEXING_SOURCE_INVALID,
        ELASTICSEARCH_BULK_REJECTED,
        ELASTICSEARCH_TRANSPORT_FAILURE,
        ELASTICSEARCH_RESPONSE_INVALID,
        INDEXING_UNEXPECTED_FAILURE
    }

    enum FailureStage {
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
