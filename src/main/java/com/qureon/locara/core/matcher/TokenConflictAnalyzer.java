package com.qureon.locara.core.matcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles semantic tokenization, stopword normalization, numeric discrepancy detection,
 * and distinguishing qualifier conflict guards for banking/mobile UI strings.
 */
final class TokenConflictAnalyzer {

    private static final Pattern STOPWORD_SPLIT_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern NUMERIC_TOKEN_PATTERN = Pattern.compile("\\b\\d+\\b");
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z])([A-Z])");
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[_\\-\\.\\:\\s/&'()+,;\"#@!?\\[\\]]+");
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^a-z0-9]");

    private static final Set<String> CONJUNCTION_STOPWORDS = new HashSet<>(Arrays.asList("dan", "and", "atau", "or"));
    private static final Set<String> WIDGET_AND_STOPWORD_TOKENS = new HashSet<>(Arrays.asList(
            "btn", "button", "tombol", "tv", "txt", "text", "textview", "lbl", "label",
            "et", "edittext", "input", "field", "kolom", "ic", "icon", "iv", "img", "imageview",
            "view", "viewgroup", "groupview", "dan", "and", "atau", "or", "di", "ke", "dari",
            "untuk", "yang", "pada", "dengan", "the", "to", "of", "in", "for"
    ));
    private static final Map<String, String[]> COMPOUND_SPLITS = Map.of(
            "topup", new String[]{"top", "up"},
            "estatement", new String[]{"e", "statement"},
            "ewallet", new String[]{"e", "wallet"},
            "mbanking", new String[]{"m", "banking"},
            "signin", new String[]{"sign", "in"},
            "signup", new String[]{"sign", "up"},
            "menyetujui", new String[]{"setuju"},
            "disetujui", new String[]{"setuju"},
            "persetujuan", new String[]{"setuju"}
    );
    private static final String[][] COMPOUND_JOINS = {
            {"top", "up", "topup"},
            {"kirim", "uang", "kirimuang"},
            {"isi", "ulang", "isiulang"},
            {"muat", "ulang", "muatulang"},
            {"buat", "akun", "buatakun"},
            {"sign", "in", "signin"},
            {"sign", "up", "signup"},
            {"log", "in", "login"}
    };

    private TokenConflictAnalyzer() {}

    static String normalize(String str) {
        return str == null ? "" : NON_ALNUM_PATTERN.matcher(str.toLowerCase()).replaceAll("").trim();
    }

    static String normalizeWithoutStopwords(String str) {
        if (str == null) return "";
        String[] words = STOPWORD_SPLIT_PATTERN.split(str.toLowerCase().replace("&", "dan"));
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty() && !CONJUNCTION_STOPWORDS.contains(w)) {
                sb.append(w);
            }
        }
        return sb.toString();
    }

    static Set<String> tokenize(String input) {
        Set<String> set = new HashSet<>();
        if (input == null) return set;
        String[] parts = TOKEN_SPLIT_PATTERN.split(CAMEL_CASE_PATTERN.matcher(input).replaceAll("$1 $2"));
        for (String p : parts) {
            String norm = normalize(p);
            if (norm.isEmpty() || CONJUNCTION_STOPWORDS.contains(norm)) continue;
            set.add(norm);
            String[] expansions = COMPOUND_SPLITS.get(norm);
            if (expansions != null) {
                set.addAll(Arrays.asList(expansions));
            }
        }
        for (String[] joinRule : COMPOUND_JOINS) {
            if (set.contains(joinRule[0]) && set.contains(joinRule[1])) {
                set.add(joinRule[2]);
            }
        }
        return set;
    }

    static boolean hasDisjointNumericTokens(String s1, String s2) {
        Set<String> nums1 = extractNumericTokens(s1);
        Set<String> nums2 = extractNumericTokens(s2);
        if (nums1.isEmpty() || nums2.isEmpty()) return false;
        for (String n : nums1) {
            if (nums2.contains(n)) return false;
        }
        return true;
    }

    static boolean hasConflictingDistinguishingTokens(String s1, String s2) {
        Set<String> raw1 = tokenize(s1);
        Set<String> raw2 = tokenize(s2);
        Set<String> m1 = filterMeaningfulTokens(raw1);
        Set<String> m2 = filterMeaningfulTokens(raw2);
        if ((m1.size() < 2 && m2.size() < 2) || m1.isEmpty() || m2.isEmpty()) return false;

        int matched1 = 0, unmatched1 = 0;
        for (String a : m1) {
            if (matchesAnyToken(a, m2, raw2)) matched1++; else unmatched1++;
        }
        int matched2 = 0, unmatched2 = 0;
        for (String b : m2) {
            if (matchesAnyToken(b, m1, raw1)) matched2++; else unmatched2++;
        }
        return (matched1 > 0 || matched2 > 0) && unmatched1 > 0 && unmatched2 > 0;
    }

    private static Set<String> extractNumericTokens(String input) {
        Set<String> nums = new HashSet<>();
        if (input == null) return nums;
        Matcher m = NUMERIC_TOKEN_PATTERN.matcher(input);
        while (m.find()) nums.add(m.group());
        return nums;
    }

    private static Set<String> filterMeaningfulTokens(Set<String> tokens) {
        Set<String> filtered = new HashSet<>();
        for (String t : tokens) {
            if ((t.length() > 1 || Character.isDigit(t.charAt(0))) && !WIDGET_AND_STOPWORD_TOKENS.contains(t)) {
                filtered.add(t);
            }
        }
        return filtered;
    }

    private static boolean matchesAnyToken(String token, Set<String> otherMeaningful, Set<String> otherRaw) {
        if (otherMeaningful.contains(token) || otherRaw.contains(token)) return true;
        for (String other : otherMeaningful) {
            if (UniversalSemanticDictionary.isSynonym(token, other)) return true;
            if (token.length() >= 4 && other.length() >= 4) {
                if (StringSimilarity.jaroWinkler(token, other) >= 0.86 || token.contains(other) || other.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }
}
