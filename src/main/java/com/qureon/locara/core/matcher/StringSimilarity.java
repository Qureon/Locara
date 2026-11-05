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
        double syn = UniversalSemanticDictionary.calculateSemanticAffinity(candidate, target);

        // Contains bonus: If target text is directly contained in candidate or vice versa (min 3 chars)
        // Guard against asymmetric single-word inclusion (e.g. "Zaku" vs "Selamat datang Di Zaku" length ratio 0.18)
        String cNorm = normalize(candidate);
        String tNorm = normalize(target);
        double containsBonus = 0.0;
        if (!cNorm.isEmpty() && !tNorm.isEmpty() && cNorm.length() >= 3 && tNorm.length() >= 3) {
            int minLen = Math.min(cNorm.length(), tNorm.length());
            int maxLen = Math.max(cNorm.length(), tNorm.length());
            double lenRatio = (double) minLen / maxLen;
            if (lenRatio >= 0.60) {
                if (cNorm.contains(tNorm) || tNorm.contains(cNorm) ||
                    (!cClean.isEmpty() && !tClean.isEmpty() && (cClean.contains(tClean) || tClean.contains(cClean)))) {
                    containsBonus = 0.15;
                }
            }
        }

        double raw = (0.45 * jw) + (0.25 * lev) + (0.15 * tok) + (0.15 * syn) + containsBonus;
        if (syn >= 0.99) {
            raw = Math.max(raw, 0.88);
        }

        // Guard 1: Disjoint Numeric Token Conflict (e.g. "Tenor 3 Bulan" vs "Tenor 6 Bulan", "1 Day" vs "7 Day")
        if (hasDisjointNumericTokens(candidate, target)) {
            return Math.min(raw, 0.40);
        }

        // Guard 2: Multi-Token Conflicting Qualifier (e.g. "Transfer RTGS" vs "Transfer SKN",
        // "Nomor Rekening Sumber" vs "Nomor Rekening Tujuan", "Kartu Kredit" vs "Kartu Debit",
        // "Password Lama" vs "Password Baru")
        if (hasConflictingDistinguishingTokens(candidate, target)) {
            return Math.min(raw, 0.55);
        }

        return Math.min(1.0, Math.max(0.0, raw));
    }

    private static final Set<String> WIDGET_AND_STOPWORD_TOKENS = new HashSet<>(Arrays.asList(
            "btn", "button", "tombol", "tv", "txt", "text", "textview", "lbl", "label",
            "et", "edittext", "input", "field", "kolom", "ic", "icon", "iv", "img", "imageview",
            "view", "viewgroup", "groupview", "dan", "and", "atau", "or", "di", "ke", "dari",
            "untuk", "yang", "pada", "dengan", "the", "to", "of", "in", "for"
    ));

    private static boolean hasDisjointNumericTokens(String s1, String s2) {
        Set<String> nums1 = extractNumericTokens(s1);
        Set<String> nums2 = extractNumericTokens(s2);
        if (nums1.isEmpty() || nums2.isEmpty()) {
            return false;
        }
        for (String n : nums1) {
            if (nums2.contains(n)) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> extractNumericTokens(String input) {
        Set<String> nums = new HashSet<>();
        if (input == null) return nums;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b\\d+\\b").matcher(input);
        while (m.find()) {
            nums.add(m.group());
        }
        return nums;
    }

    private static boolean hasConflictingDistinguishingTokens(String s1, String s2) {
        Set<String> raw1 = tokenize(s1);
        Set<String> raw2 = tokenize(s2);
        Set<String> m1 = filterMeaningfulTokens(raw1);
        Set<String> m2 = filterMeaningfulTokens(raw2);

        if (m1.size() < 2 && m2.size() < 2) {
            return false;
        }
        if (m1.isEmpty() || m2.isEmpty()) {
            return false;
        }

        int matched1 = 0;
        int unmatched1 = 0;
        for (String a : m1) {
            if (matchesAnyToken(a, m2, raw2)) {
                matched1++;
            } else {
                unmatched1++;
            }
        }

        int matched2 = 0;
        int unmatched2 = 0;
        for (String b : m2) {
            if (matchesAnyToken(b, m1, raw1)) {
                matched2++;
            } else {
                unmatched2++;
            }
        }

        // A conflicting sibling occurs when both strings share at least one head/context token
        // (e.g., "Transfer", "Nomor Rekening", "Kartu", "Password") AND both strings possess
        // mutually unmatched distinguishing tokens (e.g., "RTGS" vs "SKN", "Sumber" vs "Tujuan").
        return (matched1 > 0 || matched2 > 0) && unmatched1 > 0 && unmatched2 > 0;
    }

    private static Set<String> filterMeaningfulTokens(Set<String> tokens) {
        Set<String> filtered = new HashSet<>();
        for (String t : tokens) {
            if (t.length() <= 1 && !Character.isDigit(t.charAt(0))) {
                continue;
            }
            if (WIDGET_AND_STOPWORD_TOKENS.contains(t)) {
                continue;
            }
            filtered.add(t);
        }
        return filtered;
    }

    private static boolean matchesAnyToken(String token, Set<String> otherMeaningful, Set<String> otherRaw) {
        if (otherMeaningful.contains(token) || otherRaw.contains(token)) {
            return true;
        }
        for (String other : otherMeaningful) {
            if (UniversalSemanticDictionary.isSynonym(token, other)) {
                return true;
            }
            if (token.length() >= 4 && other.length() >= 4 && jaroWinkler(token, other) >= 0.86) {
                return true;
            }
            if (token.length() >= 4 && other.length() >= 4 && (token.contains(other) || other.contains(token))) {
                return true;
            }
        }
        return false;
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
                // General mobile compound word expansion
                if (norm.equals("topup")) {
                    set.add("top");
                    set.add("up");
                } else if (norm.equals("estatement")) {
                    set.add("e");
                    set.add("statement");
                } else if (norm.equals("ewallet")) {
                    set.add("e");
                    set.add("wallet");
                } else if (norm.equals("mbanking")) {
                    set.add("m");
                    set.add("banking");
                } else if (norm.equals("signin")) {
                    set.add("sign");
                    set.add("in");
                } else if (norm.equals("signup")) {
                    set.add("sign");
                    set.add("up");
                } else if (norm.equals("menyetujui") || norm.equals("disetujui") || norm.equals("persetujuan")) {
                    set.add("setuju");
                }
            }
        }
        if (set.contains("top") && set.contains("up")) {
            set.add("topup");
        }
        if (set.contains("kirim") && set.contains("uang")) {
            set.add("kirimuang");
        }
        if (set.contains("isi") && set.contains("ulang")) {
            set.add("isiulang");
        }
        if (set.contains("muat") && set.contains("ulang")) {
            set.add("muatulang");
        }
        if (set.contains("buat") && set.contains("akun")) {
            set.add("buatakun");
        }
        if (set.contains("sign") && set.contains("in")) {
            set.add("signin");
        }
        if (set.contains("sign") && set.contains("up")) {
            set.add("signup");
        }
        if (set.contains("log") && set.contains("in")) {
            set.add("login");
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
