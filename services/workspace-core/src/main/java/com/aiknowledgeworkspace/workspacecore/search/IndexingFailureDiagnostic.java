package com.aiknowledgeworkspace.workspacecore.search;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import org.springframework.util.StringUtils;

final class IndexingFailureDiagnostic {

    private static final int MAX_DIAGNOSTIC_LENGTH = 1024;
    private static final int MAX_SEGMENT_INDEXES = 16;

    private IndexingFailureDiagnostic() {
    }

    static String from(
            List<?> transcriptRows,
            Category category,
            FailureStage failureStage,
            RuntimeException exception
    ) {
        String diagnostic = String.join(";",
                "diagnosticVersion=1",
                "category=" + category.name(),
                "failureStage=" + failureStage.value,
                "exception=" + safeExceptionClass(exception),
                "usableRows=" + transcriptRows.size(),
                "blankRowsAfterFilter=" + blankRowsAfterFilter(transcriptRows),
                "segmentIndexes=" + segmentIndexSummary(transcriptRows),
                "missingSegmentIndexes=" + missingSegmentIndexCount(transcriptRows),
                "textLengthMin=" + textLengthMin(transcriptRows),
                "textLengthMax=" + textLengthMax(transcriptRows),
                "textLengthBuckets=" + textLengthBuckets(transcriptRows)
        );
        if (diagnostic.length() <= MAX_DIAGNOSTIC_LENGTH) {
            return diagnostic;
        }
        return diagnostic.substring(0, MAX_DIAGNOSTIC_LENGTH);
    }

    private static String safeExceptionClass(RuntimeException exception) {
        return exception == null ? "none" : exception.getClass().getSimpleName();
    }

    private static long blankRowsAfterFilter(List<?> transcriptRows) {
        return transcriptRows.stream()
                .filter(row -> !StringUtils.hasText(text(row)))
                .count();
    }

    private static String segmentIndexSummary(List<?> transcriptRows) {
        List<Integer> segmentIndexes = transcriptRows.stream()
                .map(IndexingFailureDiagnostic::segmentIndex)
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

    private static long missingSegmentIndexCount(List<?> transcriptRows) {
        return transcriptRows.stream()
                .filter(row -> segmentIndex(row) == null)
                .count();
    }

    private static String textLengthMin(List<?> transcriptRows) {
        return transcriptRows.stream()
                .map(IndexingFailureDiagnostic::text)
                .filter(text -> text != null)
                .mapToInt(String::length)
                .min()
                .stream()
                .mapToObj(String::valueOf)
                .findFirst()
                .orElse("none");
    }

    private static String textLengthMax(List<?> transcriptRows) {
        return transcriptRows.stream()
                .map(IndexingFailureDiagnostic::text)
                .filter(text -> text != null)
                .mapToInt(String::length)
                .max()
                .stream()
                .mapToObj(String::valueOf)
                .findFirst()
                .orElse("none");
    }

    private static String textLengthBuckets(List<?> transcriptRows) {
        long nullCount = 0;
        long zeroCount = 0;
        long shortCount = 0;
        long mediumCount = 0;
        long longCount = 0;
        long veryLongCount = 0;

        for (Object transcriptRow : transcriptRows) {
            String text = text(transcriptRow);
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

    private static Integer segmentIndex(Object transcriptRow) {
        return readAccessor(transcriptRow, "segmentIndex", Integer.class);
    }

    private static String text(Object transcriptRow) {
        return readAccessor(transcriptRow, "text", String.class);
    }

    private static <T> T readAccessor(Object transcriptRow, String accessorName, Class<T> expectedType) {
        if (transcriptRow == null) {
            return null;
        }
        try {
            Method accessor = transcriptRow.getClass().getMethod(accessorName);
            Object value = accessor.invoke(transcriptRow);
            if (value == null) {
                return null;
            }
            if (expectedType.isInstance(value)) {
                return expectedType.cast(value);
            }
            throw new IllegalStateException("Unexpected transcript row metadata type");
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
            throw new IllegalStateException("Unable to read transcript row metadata", exception);
        }
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
