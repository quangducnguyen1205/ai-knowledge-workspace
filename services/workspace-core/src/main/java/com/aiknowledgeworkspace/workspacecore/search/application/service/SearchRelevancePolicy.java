package com.aiknowledgeworkspace.workspacecore.search.application.service;

import com.aiknowledgeworkspace.workspacecore.search.application.port.out.TranscriptSearchHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
        List<TranscriptSearchHit> relevantHits = hits.stream()
                .filter(hit -> matchesEnoughMeaningfulTerms(hit, meaningfulTerms, requiredTerms))
                .toList();
        return workspaceWide
                ? selectWorkspaceWide(relevantHits)
                : selectAssetScoped(relevantHits);
    }

    private static List<TranscriptSearchHit> selectWorkspaceWide(List<TranscriptSearchHit> relevantHits) {
        Map<UUID, List<TranscriptSearchHit>> candidatesByAsset = new HashMap<>();
        for (TranscriptSearchHit hit : relevantHits) {
            candidatesByAsset
                    .computeIfAbsent(hit.assetId(), ignored -> new ArrayList<>())
                    .add(hit);
        }

        List<List<TranscriptSearchHit>> momentsByAsset = candidatesByAsset.values().stream()
                .map(assetCandidates -> {
                    List<TranscriptSearchHit> rankedAssetCandidates = assetCandidates.stream()
                            .sorted(HIT_ORDER)
                            .toList();
                    return deduplicateAdjacentMoments(rankedAssetCandidates).stream()
                            .limit(MAX_RESULTS_PER_ASSET)
                            .toList();
                })
                .toList();

        List<TranscriptSearchHit> flattened = new ArrayList<>();
        for (int round = 0; round < MAX_RESULTS_PER_ASSET && flattened.size() < MAX_RESULTS; round++) {
            List<TranscriptSearchHit> roundHits = new ArrayList<>();
            for (List<TranscriptSearchHit> assetMoments : momentsByAsset) {
                if (assetMoments.size() > round) {
                    roundHits.add(assetMoments.get(round));
                }
            }
            roundHits.sort(HIT_ORDER);
            for (TranscriptSearchHit roundHit : roundHits) {
                if (flattened.size() >= MAX_RESULTS) {
                    break;
                }
                flattened.add(roundHit);
            }
        }
        return List.copyOf(flattened);
    }

    private static List<TranscriptSearchHit> selectAssetScoped(List<TranscriptSearchHit> relevantHits) {
        List<TranscriptSearchHit> rankedHits = relevantHits.stream()
                .sorted(HIT_ORDER)
                .toList();
        return deduplicateAdjacentMoments(rankedHits).stream()
                .limit(MAX_RESULTS)
                .toList();
    }

    private static List<TranscriptSearchHit> deduplicateAdjacentMoments(
            List<TranscriptSearchHit> rankedHits
    ) {
        Map<UUID, Map<Integer, Integer>> runStartByAssetAndSegment = adjacentRunStarts(rankedHits);
        Set<AdjacentMomentCluster> representedClusters = new HashSet<>();
        List<TranscriptSearchHit> representatives = new ArrayList<>();

        for (TranscriptSearchHit hit : rankedHits) {
            if (hit.segmentIndex() == null) {
                representatives.add(hit);
                continue;
            }

            int runStart = runStartByAssetAndSegment.get(hit.assetId()).get(hit.segmentIndex());
            if (representedClusters.add(new AdjacentMomentCluster(hit.assetId(), runStart))) {
                representatives.add(hit);
            }
        }

        return List.copyOf(representatives);
    }

    private static Map<UUID, Map<Integer, Integer>> adjacentRunStarts(
            List<TranscriptSearchHit> rankedHits
    ) {
        Map<UUID, TreeSet<Integer>> orderedSegmentsByAsset = new HashMap<>();
        for (TranscriptSearchHit hit : rankedHits) {
            if (hit.segmentIndex() != null) {
                orderedSegmentsByAsset
                        .computeIfAbsent(hit.assetId(), ignored -> new TreeSet<>())
                        .add(hit.segmentIndex());
            }
        }

        Map<UUID, Map<Integer, Integer>> runStartByAssetAndSegment = new HashMap<>();
        orderedSegmentsByAsset.forEach((assetId, segmentIndexes) -> {
            Map<Integer, Integer> runStartBySegment = new HashMap<>();
            Integer previous = null;
            Integer runStart = null;
            for (Integer segmentIndex : segmentIndexes) {
                if (previous == null || (long) segmentIndex - previous != 1L) {
                    runStart = segmentIndex;
                }
                runStartBySegment.put(segmentIndex, runStart);
                previous = segmentIndex;
            }
            runStartByAssetAndSegment.put(assetId, runStartBySegment);
        });
        return runStartByAssetAndSegment;
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

    private record AdjacentMomentCluster(UUID assetId, int runStartSegmentIndex) {
    }
}
