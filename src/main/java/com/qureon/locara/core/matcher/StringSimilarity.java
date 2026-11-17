package com.qureon.locara.core.matcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Text similarity utility implementing Jaro-Winkler, Levenshtein, and Semantic Token Overlap algorithms.
 */
public final class StringSimilarity {

    private static final double CONTAINS_LEN_RATIO_MIN = 0.60;
    private static final double CONTAINS_BONUS = 0.15;
    private static final double EXACT_SYNONYM_FLOOR = 0.88;
    private static final double DISJOINT_NUMERIC_CAP = 0.40;
    private static final double CONFLICT_QUALIFIER_CAP = 0.55;

    private StringSimilarity() {}

    public static double jaroWinkler(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        String clean1 = normalize(s1);
        String clean2 = normalize(s2);
        if (clean1.equals(clean2)) return 1.0;
        if (clean1.isEmpty() || clean2.isEmpty()) return 0.0;

        int[] matchIndexes = matches(clean1, clean2);
        int matches = matchIndexes[0];
        if (matches == 0) return 0.0;

        double jaro = ((matches / (double) clean1.length())
                + (matches / (double) clean2.length())
                + ((matches - matchIndexes[1] / 2.0) / matches)) / 3.0;
        return jaro + (matchIndexes[2] * 0.1 * (1.0 - jaro));
    }

    public static double levenshteinRatio(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        String clean1 = normalize(s1);
        String clean2 = normalize(s2);
        if (clean1.equals(clean2)) return 1.0;
        if (clean1.isEmpty() || clean2.isEmpty()) return 0.0;
        int distance = levenshteinDistance(clean1, clean2);
        return 1.0 - ((double) distance / Math.max(clean1.length(), clean2.length()));
    }

    public static double tokenOverlap(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        Set<String> tokens1 = tokenize(s1);
        Set<String> tokens2 = tokenize(s2);
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);
        return (double) intersection.size() / Math.max(tokens1.size(), tokens2.size());
    }

    public static String normalizeWithoutStopwords(String str) {
        return TokenConflictAnalyzer.normalizeWithoutStopwords(str);
    }

    public static Set<String> tokenize(String input) {
        return TokenConflictAnalyzer.tokenize(input);
    }

    public static String normalize(String str) {
        return TokenConflictAnalyzer.normalize(str);
    }

    public static double compositeScore(String candidate, String target) {
        if (candidate == null || target == null || candidate.trim().isEmpty() || target.trim().isEmpty()) {
            return 0.0;
        }
        String cClean = normalizeWithoutStopwords(candidate);
        String tClean = normalizeWithoutStopwords(target);
        if (!cClean.isEmpty() && cClean.equals(tClean)) return 1.0;

        double jw = jaroWinkler(candidate, target);
        double lev = levenshteinRatio(candidate, target);
        double tok = tokenOverlap(candidate, target);
        double syn = UniversalSemanticDictionary.calculateSemanticAffinity(candidate, target);

        String cNorm = normalize(candidate);
        String tNorm = normalize(target);
        double containsBonus = 0.0;
        if (!cNorm.isEmpty() && !tNorm.isEmpty() && cNorm.length() >= 3 && tNorm.length() >= 3) {
            double lenRatio = (double) Math.min(cNorm.length(), tNorm.length()) / Math.max(cNorm.length(), tNorm.length());
            if (lenRatio >= CONTAINS_LEN_RATIO_MIN && (cNorm.contains(tNorm) || tNorm.contains(cNorm)
                    || (!cClean.isEmpty() && !tClean.isEmpty() && (cClean.contains(tClean) || tClean.contains(cClean))))) {
                containsBonus = CONTAINS_BONUS;
            }
        }

        double raw = (0.45 * jw) + (0.25 * lev) + (0.15 * tok) + (0.15 * syn) + containsBonus;
        if (syn >= 0.99) raw = Math.max(raw, EXACT_SYNONYM_FLOOR);

        if (TokenConflictAnalyzer.hasDisjointNumericTokens(candidate, target)) {
            return Math.min(raw, DISJOINT_NUMERIC_CAP);
        }
        if (TokenConflictAnalyzer.hasConflictingDistinguishingTokens(candidate, target)) {
            return Math.min(raw, CONFLICT_QUALIFIER_CAP);
        }
        return Math.min(1.0, Math.max(0.0, raw));
    }

    private static int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) costs[j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }

    private static int[] matches(String s1, String s2) {
        String max = s1.length() >= s2.length() ? s1 : s2;
        String min = s1.length() < s2.length() ? s1 : s2;
        int range = Math.max(max.length() / 2 - 1, 0);
        int[] matchIndexes = new int[min.length()];
        Arrays.fill(matchIndexes, -1);
        boolean[] matchFlags = new boolean[max.length()];
        int matches = 0;

        for (int mi = 0; mi < min.length(); mi++) {
            char c1 = min.charAt(mi);
            for (int xi = Math.max(mi - range, 0), xn = Math.min(mi + range + 1, max.length()); xi < xn; xi++) {
                if (!matchFlags[xi] && c1 == max.charAt(xi)) {
                    matchIndexes[mi] = xi;
                    matchFlags[xi] = true;
                    matches++;
                    break;
                }
            }
        }

        char[] ms1 = new char[matches];
        char[] ms2 = new char[matches];
        for (int i = 0, si = 0; i < min.length(); i++) if (matchIndexes[i] != -1) ms1[si++] = min.charAt(i);
        for (int i = 0, si = 0; i < max.length(); i++) if (matchFlags[i]) ms2[si++] = max.charAt(i);

        int transpositions = 0;
        for (int mi = 0; mi < ms1.length; mi++) if (ms1[mi] != ms2[mi]) transpositions++;

        int prefix = 0;
        for (int mi = 0; mi < Math.min(4, min.length()); mi++) {
            if (s1.charAt(mi) == s2.charAt(mi)) prefix++;
            else break;
        }
        return new int[]{matches, transpositions, prefix};
    }
}
