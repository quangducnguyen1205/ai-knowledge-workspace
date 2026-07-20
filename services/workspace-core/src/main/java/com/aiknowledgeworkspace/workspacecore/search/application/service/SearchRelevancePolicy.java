package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SearchRelevancePolicy {

    static final int MAX_RESULTS = 12;
    static final int MAX_RESULTS_PER_ASSET = 3;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> GENERIC_TERMS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "how", "in", "is", "it",
            "of", "on", "or", "that", "the", "this", "to", "was", "what", "when", "where", "which",
            "who", "why", "with"
    );
    private static final Comparator<TranscriptSearchHit> HIT_ORDER = Comparator
            .comparing(TranscriptSearchHit::score, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TranscriptSearchHit::segmentIndex, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TranscriptSearchHit::assetId)
            .thenComparing(TranscriptSearchHit::transcriptRowId, Comparator.nullsLast(Comparator.naturalOrder()));

    private SearchRelevancePolicy() {
    }

    static List<String> meaningfulTerms(String query) {
        LinkedHashSet<String> terms = new LinkedHashSet<>(tokens(query));
        terms.removeAll(GENERIC_TERMS);
        return List.copyOf(terms);
    }

    static List<TranscriptSearchHit> select(
            List<TranscriptSearchHit> hits,
            List<String> meaningfulTerms,
            boolean workspaceWide
    ) {
        if (meaningfulTerms.isEmpty()) {
            return List.of();
        }

        int requiredTerms = requiredTermCount(meaningfulTerms.size());
        int perAssetLimit = workspaceWide ? MAX_RESULTS_PER_ASSET : MAX_RESULTS;
        Map<UUID, Integer> acceptedPerAsset = new HashMap<>();
        List<TranscriptSearchHit> accepted = new ArrayList<>();

        hits.stream()
                .filter(hit -> matchesEnoughMeaningfulTerms(hit, meaningfulTerms, requiredTerms))
                .sorted(HIT_ORDER)
                .forEachOrdered(hit -> {
                    if (accepted.size() >= MAX_RESULTS) {
                        return;
                    }
                    int acceptedForAsset = acceptedPerAsset.getOrDefault(hit.assetId(), 0);
                    if (acceptedForAsset >= perAssetLimit) {
                        return;
                    }
                    accepted.add(hit);
                    acceptedPerAsset.put(hit.assetId(), acceptedForAsset + 1);
                });

        return List.copyOf(accepted);
    }

    private static boolean matchesEnoughMeaningfulTerms(
            TranscriptSearchHit hit,
            List<String> meaningfulTerms,
            int requiredTerms
    ) {
        Set<String> candidateTerms = new LinkedHashSet<>(tokens(hit.assetTitle()));
        candidateTerms.addAll(tokens(hit.text()));
        long matchedTerms = meaningfulTerms.stream().filter(candidateTerms::contains).count();
        return matchedTerms >= requiredTerms;
    }

    private static int requiredTermCount(int termCount) {
        return termCount <= 2 ? termCount : (termCount * 2 + 2) / 3;
    }

    private static List<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        Matcher matcher = TOKEN_PATTERN.matcher(value.toLowerCase(Locale.ROOT));
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }
}
