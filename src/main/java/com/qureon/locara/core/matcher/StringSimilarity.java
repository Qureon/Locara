package com.qureon.locara.core.matcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Text similarity utility implementing Jaro-Winkler, Levenshtein, and Semantic Token Overlap algorithms.
 */
public final class StringSimilarity {

    private StringSimilarity() {}

    /**
     * Calculates the Jaro-Winkler similarity between two strings (0.0 to 1.0).
     */
    public static double jaroWinkler(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        String clean1 = normalize(s1);
        String clean2 = normalize(s2);

        if (clean1.equals(clean2)) return 1.0;
        if (clean1.isEmpty() || clean2.isEmpty()) return 0.0;

        int[] matchIndexes = matches(clean1, clean2);
        int matches = matchIndexes[0];
        int transpositions = matchIndexes[1];
        int prefix = matchIndexes[2];

        if (matches == 0) return 0.0;

        double jaro = ((matches / (double) clean1.length()) +
                (matches / (double) clean2.length()) +
                ((matches - transpositions / 2.0) / matches)) / 3.0;

        double p = 0.1; // Standard scaling factor
        return jaro + (prefix * p * (1.0 - jaro));
    }

    /**
     * Calculates Levenshtein similarity ratio between 0.0 and 1.0.
     */
    public static double levenshteinRatio(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        String clean1 = normalize(s1);
        String clean2 = normalize(s2);

        if (clean1.equals(clean2)) return 1.0;
        if (clean1.isEmpty() || clean2.isEmpty()) return 0.0;

        int distance = levenshteinDistance(clean1, clean2);
        int maxLen = Math.max(clean1.length(), clean2.length());
        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * Token overlap ratio (e.g. tokenizes "btn_lanjut_transfer" into ["btn", "lanjut", "transfer"]
     * and compares with "Lanjut Transfer").
     */
    public static double tokenOverlap(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        Set<String> tokens1 = tokenize(s1);
        Set<String> tokens2 = tokenize(s2);

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        int totalUnique = Math.max(tokens1.size(), tokens2.size());
        return (double) intersection.size() / totalUnique;
    }

    /**
     * Normalizes a string by converting & to 'dan', lowercasing, and stripping punctuation and common Indonesian/English stopwords.
     */
    public static String normalizeWithoutStopwords(String str) {
        if (str == null) return "";
        String s = str.toLowerCase().replace("&", "dan");
        String[] words = s.split("[^a-z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (w.equals("dan") || w.equals("and") || w.equals("atau") || w.equals("or")) {
                continue;
            }
            sb.append(w);
        }
        return sb.toString();
    }

    /**
     * Hybrid score combining Jaro-Winkler, Levenshtein, and Token Overlap.
     */
    public static double compositeScore(String candidate, String target) {
        if (candidate == null || target == null || candidate.trim().isEmpty() || target.trim().isEmpty()) {
            return 0.0;
        }

        // Exact match or exact match modulo stopwords (e.g. "Topup & Bayar" vs "Top Up dan Bayar")
        String cClean = normalizeWithoutStopwords(candidate);
        String tClean = normalizeWithoutStopwords(target);
        if (!cClean.isEmpty() && cClean.equals(tClean)) {
            return 1.0;
        }

        double jw = jaroWinkler(candidate, target);
        double lev = levenshteinRatio(candidate, target);
        double tok = tokenOverlap(candidate, target);

        // Contains bonus: If target text is directly contained in candidate or vice versa (min 3 chars)
        String cNorm = normalize(candidate);
        String tNorm = normalize(target);
        double containsBonus = 0.0;
        if (!cNorm.isEmpty() && !tNorm.isEmpty() && cNorm.length() >= 3 && tNorm.length() >= 3) {
            if (cNorm.contains(tNorm) || tNorm.contains(cNorm) ||
                (!cClean.isEmpty() && !tClean.isEmpty() && (cClean.contains(tClean) || tClean.contains(cClean)))) {
                containsBonus = 0.15;
            }
        }

        double raw = (0.50 * jw) + (0.25 * lev) + (0.25 * tok) + containsBonus;
        return Math.min(1.0, Math.max(0.0, raw));
    }

    public static Set<String> tokenize(String input) {
        Set<String> set = new HashSet<>();
        if (input == null) return set;
        // Split on camelCase, underscores, dashes, dots, colons, spaces, ampersand, quotes, parentheses, slashes, plus
        String[] parts = input.replaceAll("([a-z])([A-Z])", "$1 $2")
                .split("[_\\-\\.\\:\\s/&'()+,;\"#@!?\\[\\]]+");
        for (String p : parts) {
            String norm = normalize(p);
            if (!norm.isEmpty()) {
                // Ignore conjunction stopwords in token set to ensure symetry between & and 'dan'
                if (norm.equals("dan") || norm.equals("and") || norm.equals("atau") || norm.equals("or")) {
                    continue;
                }
                set.add(norm);
                // Banking compound word expansion
                if (norm.equals("topup")) {
                    set.add("top");
                    set.add("up");
                } else if (norm.equals("estatement")) {
                    set.add("e");
                    set.add("statement");
                } else if (norm.equals("msmile")) {
                    set.add("m");
                    set.add("smile");
                } else if (norm.equals("mmoney")) {
                    set.add("m");
                    set.add("money");
                }
            }
        }
        if (set.contains("top") && set.contains("up")) {
            set.add("topup");
        }
        return set;
    }

    public static String normalize(String str) {
        if (str == null) return "";
        return str.toLowerCase().replaceAll("[^a-z0-9]", "").trim();
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
        for (int i = 0, si = 0; i < min.length(); i++) {
            if (matchIndexes[i] != -1) {
                ms1[si] = min.charAt(i);
                si++;
            }
        }
        for (int i = 0, si = 0; i < max.length(); i++) {
            if (matchFlags[i]) {
                ms2[si] = max.charAt(i);
                si++;
            }
        }

        int transpositions = 0;
        for (int mi = 0; mi < ms1.length; mi++) {
            if (ms1[mi] != ms2[mi]) transpositions++;
        }

        int prefix = 0;
        for (int mi = 0; mi < Math.min(4, min.length()); mi++) {
            if (s1.charAt(mi) == s2.charAt(mi)) prefix++;
            else break;
        }

        return new int[]{matches, transpositions, prefix};
    }
}
