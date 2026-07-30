package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContext;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextRow;
import java.util.ArrayList;
import java.util.List;

final class SearchContextSnippetPolicy {

    static final int MAX_CODE_POINTS = 600;
    static final int PREVIOUS_CODE_POINTS = 149;
    static final int HIT_CODE_POINTS = 300;
    static final int NEXT_CODE_POINTS = 149;
    private static final String ELLIPSIS = "…";

    private SearchContextSnippetPolicy() {
    }

    static String format(SearchCanonicalContext context) {
        if (context == null || context.matchedRow() == null) {
            throw new IllegalArgumentException("Canonical context must contain a matched row");
        }

        List<SearchCanonicalContextRow> rows = context.orderedRows();
        int hitIndex = rows.indexOf(context.matchedRow());
        if (hitIndex < 0) {
            throw new IllegalArgumentException("Canonical context rows must contain the matched row");
        }

        String hit = normalizeText(context.matchedRow().text());
        if (hit.isEmpty()) {
            throw new IllegalArgumentException("Canonical matched row text must be usable");
        }

        String previous = hitIndex == 0 ? "" : normalizeText(rows.get(hitIndex - 1).text());
        String next = hitIndex + 1 >= rows.size() ? "" : normalizeText(rows.get(hitIndex + 1).text());
        if (previous.equals(hit)) {
            previous = "";
        }
        if (next.equals(hit)) {
            next = "";
        }

        List<String> sections = new ArrayList<>(3);
        if (!previous.isEmpty()) {
            sections.add(suffix(previous, PREVIOUS_CODE_POINTS));
        }
        sections.add(prefix(hit, HIT_CODE_POINTS));
        if (!next.isEmpty()) {
            sections.add(prefix(next, NEXT_CODE_POINTS));
        }
        return String.join(" ", sections);
    }

    static String normalizeText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = normalized.length() > 0;
                continue;
            }
            if (pendingSpace) {
                normalized.append(' ');
                pendingSpace = false;
            }
            normalized.appendCodePoint(codePoint);
        }
        return normalized.toString();
    }

    private static String prefix(String value, int budget) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= budget) {
            return value;
        }
        int end = value.offsetByCodePoints(0, budget - 1);
        return value.substring(0, end) + ELLIPSIS;
    }

    private static String suffix(String value, int budget) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= budget) {
            return value;
        }
        int start = value.offsetByCodePoints(0, codePointCount - budget + 1);
        return ELLIPSIS + value.substring(start);
    }
}
