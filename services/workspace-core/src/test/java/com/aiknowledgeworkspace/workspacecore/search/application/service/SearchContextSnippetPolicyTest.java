package com.aiknowledgeworkspace.workspacecore.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContext;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextRow;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchContextSnippetPolicyTest {

    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void middleRowIncludesPreviousHitAndNextInCanonicalOrder() {
        assertThat(format("Previous canonical row.", "Matching row.", "Next canonical row."))
                .isEqualTo("Previous canonical row. Matching row. Next canonical row.");
    }

    @Test
    void firstAndLastRowsUseOnlyAvailableNeighbors() {
        SearchCanonicalContextRow first = row("first", 0, "First matching row.");
        SearchCanonicalContextRow next = row("next", 1, "Next row.");
        assertThat(SearchContextSnippetPolicy.format(context(first, List.of(first, next))))
                .isEqualTo("First matching row. Next row.");

        SearchCanonicalContextRow previous = row("previous", 2, "Previous row.");
        SearchCanonicalContextRow last = row("last", 3, "Last matching row.");
        assertThat(SearchContextSnippetPolicy.format(context(last, List.of(previous, last))))
                .isEqualTo("Previous row. Last matching row.");
    }

    @Test
    void unicodeWhitespaceIsCollapsedToOneAsciiSpace() {
        assertThat(format(
                "  Previous\u00a0 canonical\trow. ",
                "\nMatching\u2003row.\r\n",
                " Next   canonical row. "
        )).isEqualTo("Previous canonical row. Matching row. Next canonical row.");
    }

    @Test
    void duplicatePreviousOrNextIsDroppedWithoutDroppingHit() {
        assertThat(format("Repeated   moment.", "Repeated moment.", "Distinct next."))
                .isEqualTo("Repeated moment. Distinct next.");
        assertThat(format("Distinct previous.", "Repeated moment.", "Repeated\u00a0moment."))
                .isEqualTo("Distinct previous. Repeated moment.");
    }

    @Test
    void emptyNeighborIsIgnored() {
        assertThat(format(" \n\t ", "Matching row.", "Next row."))
                .isEqualTo("Matching row. Next row.");
    }

    @Test
    void unicodeCombiningCharactersAndEmojiRemainUnchanged() {
        String hit = "Cafe\u0301 handles emoji 😀 safely.";

        assertThat(format(null, hit, "下一行。"))
                .isEqualTo("Cafe\u0301 handles emoji 😀 safely. 下一行。");
    }

    @Test
    void fixedBudgetsProduceAnExactSixHundredCodePointMaximum() {
        String snippet = format("p".repeat(200), "h".repeat(400), "n".repeat(200));

        assertThat(snippet.codePointCount(0, snippet.length()))
                .isEqualTo(SearchContextSnippetPolicy.MAX_CODE_POINTS);
        String[] sections = snippet.split(" ");
        assertThat(sections[0]).startsWith("…").hasSize(149);
        assertThat(sections[1]).endsWith("…").hasSize(300);
        assertThat(sections[2]).endsWith("…").hasSize(149);
    }

    @Test
    void previousKeepsSuffixWhileHitAndNextKeepPrefix() {
        String snippet = format(
                "prefix-" + "p".repeat(160) + "-nearest",
                "hit-start-" + "h".repeat(310) + "-hit-end",
                "next-start-" + "n".repeat(160) + "-next-end"
        );
        String[] sections = snippet.split(" ");

        assertThat(sections[0]).startsWith("…").endsWith("-nearest").doesNotContain("prefix-");
        assertThat(sections[1]).startsWith("hit-start-").endsWith("…").doesNotContain("-hit-end");
        assertThat(sections[2]).startsWith("next-start-").endsWith("…").doesNotContain("-next-end");
    }

    @Test
    void truncationNeverSplitsSurrogatePairs() {
        String snippet = format(null, "😀".repeat(400), null);

        assertThat(snippet.codePointCount(0, snippet.length())).isEqualTo(300);
        assertThat(snippet).endsWith("…");
        assertThat(snippet.substring(0, snippet.length() - 1))
                .doesNotContain("\uFFFD");
    }

    private String format(String previous, String hit, String next) {
        SearchCanonicalContextRow matched = row("hit", 1, hit);
        java.util.ArrayList<SearchCanonicalContextRow> rows = new java.util.ArrayList<>();
        if (previous != null) {
            rows.add(row("previous", 0, previous));
        }
        rows.add(matched);
        if (next != null) {
            rows.add(row("next", 2, next));
        }
        return SearchContextSnippetPolicy.format(context(matched, rows));
    }

    private SearchCanonicalContext context(
            SearchCanonicalContextRow matched,
            List<SearchCanonicalContextRow> rows
    ) {
        return new SearchCanonicalContext(
                ASSET_ID,
                matched.transcriptRowId(),
                matched.segmentIndex(),
                matched,
                rows
        );
    }

    private SearchCanonicalContextRow row(String id, int segmentIndex, String text) {
        return new SearchCanonicalContextRow(
                id,
                segmentIndex,
                null,
                null,
                text,
                "2026-07-30T00:00:00Z"
        );
    }
}
