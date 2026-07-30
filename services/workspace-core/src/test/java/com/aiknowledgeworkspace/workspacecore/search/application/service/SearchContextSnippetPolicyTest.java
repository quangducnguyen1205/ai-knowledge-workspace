package com.aiknowledgeworkspace.workspacecore.search.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContext;
import com.aiknowledgeworkspace.workspacecore.search.application.port.out.asset.SearchCanonicalContextRow;
import java.text.BreakIterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SearchContextSnippetPolicyTest {

    private static final UUID ASSET_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static final String ELLIPSIS = "…";
    private static final String COMBINING_GRAVE = "\u0300";
    private static final String COMBINING_ACUTE = "\u0301";
    private static final String COMBINING_CIRCUMFLEX = "\u0302";
    private static final String ZERO_WIDTH_JOINER = "\u200d";
    private static final String VARIATION_SELECTOR_16 = "\ufe0f";

    private static final String E_ACUTE = "e" + COMBINING_ACUTE;
    private static final String A_THREE_MARKS = "a" + COMBINING_GRAVE + COMBINING_ACUTE + COMBINING_CIRCUMFLEX;
    private static final String VIETNAMESE_E = "e" + COMBINING_CIRCUMFLEX + COMBINING_ACUTE;

    private static final String THUMBS_UP = "👍";
    private static final String SKIN_TONE_MEDIUM = "\uD83C\uDFFD";
    private static final String THUMBS_UP_MEDIUM = THUMBS_UP + SKIN_TONE_MEDIUM;
    private static final String WOMAN = "👩";
    private static final String GIRL = "👧";
    private static final String LAPTOP = "💻";
    private static final String WOMAN_TECHNOLOGIST = WOMAN + ZERO_WIDTH_JOINER + LAPTOP;
    private static final String FAMILY = WOMAN + ZERO_WIDTH_JOINER + WOMAN + ZERO_WIDTH_JOINER + GIRL;
    private static final String HEART = "❤";
    private static final String RED_HEART = HEART + VARIATION_SELECTOR_16;

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

        assertThat(codePoints(snippet)).isEqualTo(SearchContextSnippetPolicy.MAX_CODE_POINTS);
        String[] sections = snippet.split(" ");
        assertThat(sections[0]).startsWith(ELLIPSIS).hasSize(149);
        assertThat(sections[1]).endsWith(ELLIPSIS).hasSize(300);
        assertThat(sections[2]).endsWith(ELLIPSIS).hasSize(149);
    }

    @Test
    void previousKeepsSuffixWhileHitAndNextKeepPrefix() {
        String snippet = format(
                "prefix-" + "p".repeat(160) + "-nearest",
                "hit-start-" + "h".repeat(310) + "-hit-end",
                "next-start-" + "n".repeat(160) + "-next-end"
        );
        String[] sections = snippet.split(" ");

        assertThat(sections[0]).startsWith(ELLIPSIS).endsWith("-nearest").doesNotContain("prefix-");
        assertThat(sections[1]).startsWith("hit-start-").endsWith(ELLIPSIS).doesNotContain("-hit-end");
        assertThat(sections[2]).startsWith("next-start-").endsWith(ELLIPSIS).doesNotContain("-next-end");
    }

    @Test
    void truncationNeverSplitsSurrogatePairs() {
        String source = "😀".repeat(400);
        String section = matchingSection(source);

        assertThat(codePoints(section)).isEqualTo(300);
        assertThat(section).endsWith(ELLIPSIS);
        assertThat(section.substring(0, section.length() - 1)).doesNotContain("\uFFFD");
        assertGraphemeSafePrefix(source, section, SearchContextSnippetPolicy.HIT_CODE_POINTS);
    }

    @Test
    void decomposedAccentClusterSurvivesMatchingPrefixTruncation() {
        String source = E_ACUTE.repeat(400);
        String section = matchingSection(source);

        assertGraphemeSafePrefix(source, section, SearchContextSnippetPolicy.HIT_CODE_POINTS);
        assertThat(section).isEqualTo(E_ACUTE.repeat(149) + ELLIPSIS);
        assertThat(codePoints(section)).isEqualTo(299);
        assertThat(retainedPrefix(section)).doesNotEndWith("e");
    }

    @Test
    void decomposedAccentClusterSurvivesPreviousSuffixTruncation() {
        String source = E_ACUTE.repeat(100) + "Z";
        String section = previousSection(source);

        assertGraphemeSafeSuffix(source, section, SearchContextSnippetPolicy.PREVIOUS_CODE_POINTS);
        assertThat(section).isEqualTo(ELLIPSIS + E_ACUTE.repeat(73) + "Z");
        assertThat(codePoints(section)).isEqualTo(148);
        assertThat(retainedSuffix(section)).startsWith("e");
        assertThat(isCombiningMark(retainedSuffix(section).codePointAt(0))).isFalse();
    }

    @Test
    void multipleCombiningMarksAreRetainedOrExcludedAsOneUnit() {
        String prefixSource = A_THREE_MARKS.repeat(200);
        String prefixSection = matchingSection(prefixSource);

        assertGraphemeSafePrefix(prefixSource, prefixSection, SearchContextSnippetPolicy.HIT_CODE_POINTS);
        assertThat(prefixSection).isEqualTo(A_THREE_MARKS.repeat(74) + ELLIPSIS);
        assertThat(occurrencesOf(retainedPrefix(prefixSection), "a")).isEqualTo(74);
        assertThat(occurrencesOf(retainedPrefix(prefixSection), COMBINING_CIRCUMFLEX)).isEqualTo(74);

        String suffixSource = A_THREE_MARKS.repeat(100) + "Z";
        String suffixSection = previousSection(suffixSource);

        assertGraphemeSafeSuffix(suffixSource, suffixSection, SearchContextSnippetPolicy.PREVIOUS_CODE_POINTS);
        assertThat(suffixSection).isEqualTo(ELLIPSIS + A_THREE_MARKS.repeat(36) + "Z");
        assertThat(occurrencesOf(retainedSuffix(suffixSection), "a")).isEqualTo(36);
        assertThat(occurrencesOf(retainedSuffix(suffixSection), COMBINING_GRAVE)).isEqualTo(36);
    }

    @Test
    void decomposedVietnameseToneMarksStayAttachedToTheirBase() {
        String prefixSource = VIETNAMESE_E.repeat(250);
        String prefixSection = matchingSection(prefixSource);

        assertGraphemeSafePrefix(prefixSource, prefixSection, SearchContextSnippetPolicy.HIT_CODE_POINTS);
        assertThat(prefixSection).isEqualTo(VIETNAMESE_E.repeat(99) + ELLIPSIS);

        String suffixSource = VIETNAMESE_E.repeat(100) + "YZ";
        String suffixSection = previousSection(suffixSource);

        assertGraphemeSafeSuffix(suffixSource, suffixSection, SearchContextSnippetPolicy.PREVIOUS_CODE_POINTS);
        assertThat(suffixSection).isEqualTo(ELLIPSIS + VIETNAMESE_E.repeat(48) + "YZ");
        assertThat(occurrencesOf(retainedSuffix(suffixSection), COMBINING_ACUTE))
                .isEqualTo(occurrencesOf(retainedSuffix(suffixSection), COMBINING_CIRCUMFLEX));
    }

    @Test
    void emojiSkinToneModifierIsNeverSeparatedFromItsBase() {
        String prefixSource = THUMBS_UP_MEDIUM.repeat(200);
        String prefixSection = matchingSection(prefixSource);

        assertGraphemeSafePrefix(prefixSource, prefixSection, SearchContextSnippetPolicy.HIT_CODE_POINTS);
        assertThat(prefixSection).isEqualTo(THUMBS_UP_MEDIUM.repeat(149) + ELLIPSIS);
        assertThat(occurrencesOf(retainedPrefix(prefixSection), THUMBS_UP))
                .isEqualTo(occurrencesOf(retainedPrefix(prefixSection), SKIN_TONE_MEDIUM));

        String suffixSource = THUMBS_UP_MEDIUM.repeat(100) + "Z";
        String suffixSection = previousSection(suffixSource);

        assertGraphemeSafeSuffix(suffixSource, suffixSection, SearchContextSnippetPolicy.PREVIOUS_CODE_POINTS);
        assertThat(suffixSection).isEqualTo(ELLIPSIS + THUMBS_UP_MEDIUM.repeat(73) + "Z");
        assertThat(retainedSuffix(suffixSection)).doesNotStartWith(SKIN_TONE_MEDIUM);
    }

    @Test
    void zwjEmojiSequenceNeverLeavesADanglingJoinerOrPartialSequence() {
        String prefixSource = FAMILY.repeat(100);
        String prefixSection = matchingSection(prefixSource);

        assertGraphemeSafePrefix(prefixSource, prefixSection, SearchContextSnippetPolicy.HIT_CODE_POINTS);
        assertThat(prefixSection).isEqualTo(FAMILY.repeat(59) + ELLIPSIS);
        assertThat(retainedPrefix(prefixSection)).doesNotEndWith(ZERO_WIDTH_JOINER);
        assertThat(occurrencesOf(retainedPrefix(prefixSection), ZERO_WIDTH_JOINER)).isEqualTo(59 * 2);

        String suffixSource = WOMAN_TECHNOLOGIST.repeat(100) + "YZ";
        String suffixSection = previousSection(suffixSource);

        assertGraphemeSafeSuffix(suffixSource, suffixSection, SearchContextSnippetPolicy.PREVIOUS_CODE_POINTS);
        assertThat(suffixSection).isEqualTo(ELLIPSIS + WOMAN_TECHNOLOGIST.repeat(48) + "YZ");
        assertThat(retainedSuffix(suffixSection)).doesNotStartWith(ZERO_WIDTH_JOINER);
        assertThat(retainedSuffix(suffixSection)).doesNotStartWith(LAPTOP);
    }

    @Test
    void nextSectionPrefixTruncationIsAlsoGraphemeSafe() {
        String source = WOMAN_TECHNOLOGIST.repeat(100);
        String section = nextSection(source);

        assertGraphemeSafePrefix(source, section, SearchContextSnippetPolicy.NEXT_CODE_POINTS);
        assertThat(section).isEqualTo(WOMAN_TECHNOLOGIST.repeat(49) + ELLIPSIS);
        assertThat(retainedPrefix(section)).doesNotEndWith(ZERO_WIDTH_JOINER);
    }

    @Test
    void variationSelectorRemainsAttachedToItsBaseCharacter() {
        String prefixSource = RED_HEART.repeat(400);
        String prefixSection = matchingSection(prefixSource);

        assertGraphemeSafePrefix(prefixSource, prefixSection, SearchContextSnippetPolicy.HIT_CODE_POINTS);
        assertThat(prefixSection).isEqualTo(RED_HEART.repeat(149) + ELLIPSIS);
        assertThat(occurrencesOf(retainedPrefix(prefixSection), "❤"))
                .isEqualTo(occurrencesOf(retainedPrefix(prefixSection), VARIATION_SELECTOR_16));

        String suffixSource = RED_HEART.repeat(100) + "Z";
        String suffixSection = previousSection(suffixSource);

        assertGraphemeSafeSuffix(suffixSource, suffixSection, SearchContextSnippetPolicy.PREVIOUS_CODE_POINTS);
        assertThat(suffixSection).isEqualTo(ELLIPSIS + RED_HEART.repeat(73) + "Z");
        assertThat(retainedSuffix(suffixSection)).doesNotStartWith(VARIATION_SELECTOR_16);
    }

    @Test
    void cjkAndAsciiTruncationIsUnchangedByTheGraphemeBoundaryAdjustment() {
        String cjkSource = "下".repeat(400);
        String cjkSection = matchingSection(cjkSource);
        assertThat(cjkSection).isEqualTo(legacyPrefix(cjkSource, SearchContextSnippetPolicy.HIT_CODE_POINTS));
        assertThat(codePoints(cjkSection)).isEqualTo(SearchContextSnippetPolicy.HIT_CODE_POINTS);

        String asciiSource = "p".repeat(400);
        assertThat(matchingSection(asciiSource))
                .isEqualTo(legacyPrefix(asciiSource, SearchContextSnippetPolicy.HIT_CODE_POINTS));
        assertThat(previousSection(asciiSource))
                .isEqualTo(legacySuffix(asciiSource, SearchContextSnippetPolicy.PREVIOUS_CODE_POINTS));

        String emojiSource = "😀".repeat(400);
        assertThat(matchingSection(emojiSource))
                .isEqualTo(legacyPrefix(emojiSource, SearchContextSnippetPolicy.HIT_CODE_POINTS));
    }

    @Test
    void graphemeAdjustmentNeverExceedsTheSectionOrSnippetBudgets() {
        String snippet = format(
                FAMILY.repeat(100) + "YZ",
                THUMBS_UP_MEDIUM.repeat(200),
                A_THREE_MARKS.repeat(200)
        );
        String[] sections = snippet.split(" ");

        assertThat(codePoints(sections[0]))
                .isLessThanOrEqualTo(SearchContextSnippetPolicy.PREVIOUS_CODE_POINTS);
        assertThat(codePoints(sections[1]))
                .isLessThanOrEqualTo(SearchContextSnippetPolicy.HIT_CODE_POINTS);
        assertThat(codePoints(sections[2]))
                .isLessThanOrEqualTo(SearchContextSnippetPolicy.NEXT_CODE_POINTS);
        assertThat(codePoints(snippet))
                .isLessThanOrEqualTo(SearchContextSnippetPolicy.MAX_CODE_POINTS);
    }

    @Test
    void aClusterLargerThanItsSectionBudgetIsExcludedRatherThanSplit() {
        String oneOversizedCluster = "a" + COMBINING_ACUTE.repeat(400);

        assertThat(matchingSection(oneOversizedCluster)).isEqualTo(ELLIPSIS);
        assertThat(previousSection(oneOversizedCluster)).isEqualTo(ELLIPSIS);
        assertThat(nextSection(oneOversizedCluster)).isEqualTo(ELLIPSIS);
    }

    @Test
    void retainedTextIsAnExactUnnormalizedSliceOfTheInput() {
        String source = E_ACUTE.repeat(400);
        String retained = retainedPrefix(matchingSection(source));

        assertThat(source).startsWith(retained);
        assertThat(retained).doesNotContain("\u00e9");
        assertThat(occurrencesOf(retained, COMBINING_ACUTE)).isEqualTo(149);

        String precomposed = "\u00e9".repeat(400);
        assertThat(retainedPrefix(matchingSection(precomposed))).isEqualTo("\u00e9".repeat(299));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("prefixFixturesTheRawBoundaryWouldSplit")
    void formerRawCodePointPrefixBoundaryWouldHaveSplitTheFixture(String name, String source) {
        int budget = SearchContextSnippetPolicy.HIT_CODE_POINTS;
        int legacyCut = retainedPrefix(legacyPrefix(source, budget)).length();
        int currentCut = retainedPrefix(matchingSection(source)).length();

        assertThat(isGraphemeBoundary(source, legacyCut))
                .describedAs("%s: raw code-point cut at %s must be inside a grapheme cluster", name, legacyCut)
                .isFalse();
        assertThat(isGraphemeBoundary(source, currentCut))
                .describedAs("%s: current cut at %s must be a grapheme boundary", name, currentCut)
                .isTrue();
        assertThat(currentCut).isLessThan(legacyCut);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("suffixFixturesTheRawBoundaryWouldSplit")
    void formerRawCodePointSuffixBoundaryWouldHaveSplitTheFixture(String name, String source) {
        int budget = SearchContextSnippetPolicy.PREVIOUS_CODE_POINTS;
        int legacyCut = source.length() - retainedSuffix(legacySuffix(source, budget)).length();
        int currentCut = source.length() - retainedSuffix(previousSection(source)).length();

        assertThat(isGraphemeBoundary(source, legacyCut))
                .describedAs("%s: raw code-point cut at %s must be inside a grapheme cluster", name, legacyCut)
                .isFalse();
        assertThat(isGraphemeBoundary(source, currentCut))
                .describedAs("%s: current cut at %s must be a grapheme boundary", name, currentCut)
                .isTrue();
        assertThat(currentCut).isGreaterThan(legacyCut);
    }

    private static Stream<Arguments> prefixFixturesTheRawBoundaryWouldSplit() {
        return Stream.of(
                Arguments.of("decomposed accent", E_ACUTE.repeat(400)),
                Arguments.of("three combining marks", A_THREE_MARKS.repeat(200)),
                Arguments.of("decomposed Vietnamese", VIETNAMESE_E.repeat(250)),
                Arguments.of("emoji skin-tone modifier", THUMBS_UP_MEDIUM.repeat(200)),
                Arguments.of("zwj profession sequence", WOMAN_TECHNOLOGIST.repeat(150)),
                Arguments.of("zwj family sequence", FAMILY.repeat(100)),
                Arguments.of("variation selector", RED_HEART.repeat(400))
        );
    }

    private static Stream<Arguments> suffixFixturesTheRawBoundaryWouldSplit() {
        return Stream.of(
                Arguments.of("decomposed accent", E_ACUTE.repeat(100) + "Z"),
                Arguments.of("three combining marks", A_THREE_MARKS.repeat(100) + "Z"),
                Arguments.of("decomposed Vietnamese", VIETNAMESE_E.repeat(100) + "YZ"),
                Arguments.of("emoji skin-tone modifier", THUMBS_UP_MEDIUM.repeat(100) + "Z"),
                Arguments.of("zwj profession sequence", WOMAN_TECHNOLOGIST.repeat(100) + "YZ"),
                Arguments.of("zwj family sequence", FAMILY.repeat(100) + "Z"),
                Arguments.of("variation selector", RED_HEART.repeat(100) + "Z")
        );
    }

    private void assertGraphemeSafePrefix(String source, String section, int budget) {
        assertThat(section).endsWith(ELLIPSIS);
        String retained = retainedPrefix(section);
        assertThat(source).startsWith(retained);
        assertThat(isGraphemeBoundary(source, retained.length())).isTrue();
        assertThat(codePoints(section)).isLessThanOrEqualTo(budget);

        int nextBoundary = boundaryAfter(source, retained.length());
        assertThat(source.codePointCount(0, nextBoundary) + 1)
                .describedAs("retaining one more grapheme cluster must break the %s code-point budget", budget)
                .isGreaterThan(budget);
    }

    private void assertGraphemeSafeSuffix(String source, String section, int budget) {
        assertThat(section).startsWith(ELLIPSIS);
        String retained = retainedSuffix(section);
        assertThat(source).endsWith(retained);
        int cut = source.length() - retained.length();
        assertThat(isGraphemeBoundary(source, cut)).isTrue();
        assertThat(codePoints(section)).isLessThanOrEqualTo(budget);

        int previousBoundary = boundaryBefore(source, cut);
        assertThat(source.codePointCount(previousBoundary, source.length()) + 1)
                .describedAs("retaining one more grapheme cluster must break the %s code-point budget", budget)
                .isGreaterThan(budget);
    }

    private static String legacyPrefix(String value, int budget) {
        int codePointCount = codePoints(value);
        if (codePointCount <= budget) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, budget - 1)) + ELLIPSIS;
    }

    private static String legacySuffix(String value, int budget) {
        int codePointCount = codePoints(value);
        if (codePointCount <= budget) {
            return value;
        }
        return ELLIPSIS + value.substring(value.offsetByCodePoints(0, codePointCount - budget + 1));
    }

    private static boolean isGraphemeBoundary(String text, int offset) {
        return graphemesOf(text).isBoundary(offset);
    }

    private static int boundaryAfter(String text, int offset) {
        int next = graphemesOf(text).following(offset);
        return next == BreakIterator.DONE ? text.length() : next;
    }

    private static int boundaryBefore(String text, int offset) {
        int previous = graphemesOf(text).preceding(offset);
        return previous == BreakIterator.DONE ? 0 : previous;
    }

    private static BreakIterator graphemesOf(String text) {
        BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
        graphemes.setText(text);
        return graphemes;
    }

    private static boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static int occurrencesOf(String text, String token) {
        int count = 0;
        for (int index = text.indexOf(token); index >= 0; index = text.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }

    private static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }

    private static String retainedPrefix(String section) {
        return section.substring(0, section.length() - ELLIPSIS.length());
    }

    private static String retainedSuffix(String section) {
        return section.substring(ELLIPSIS.length());
    }

    private String matchingSection(String hit) {
        return format(null, hit, null);
    }

    private String previousSection(String previous) {
        return format(previous, "Hit.", null).split(" ")[0];
    }

    private String nextSection(String next) {
        return format(null, "Hit.", next).split(" ")[1];
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
