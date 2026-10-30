package com.qureon.locara.core.matcher;

import java.util.*;

/**
 * 🧠 LocaraIntentMatcher — Semantic Intent & Banking Error Matching Engine.
 *
 * Resolves semantic equivalence between on-screen validation errors and test assertions.
 * Bridges copywriting drift and dynamic inline text nodes for Negative Test Cases.
 */
public final class LocaraIntentMatcher {

    private static final Map<String, List<String>> CONCEPT_CLUSTERS = new LinkedHashMap<>();

    static {
        registerConcept("PHONE_LENGTH", Arrays.asList(
                "kurang dari 10 digit", "kurang dari 10", "minimal 10 digit",
                "kurang digit", "panjang nomor tidak valid", "nomor kurang dari 10 digit",
                "no handphone tidak valid karena kurang dari 10 digit",
                "invalid_phone_number", "nomor_tidak_valid", "phone_number_format",
                "kurang_dari_10_digit", "kurang dari 10 digit", "min 10 digit"
        ));

        registerConcept("PHONE_PREFIX", Arrays.asList(
                "harus diawali dengan 08", "harus diawali 08", "diawali dengan 08",
                "diawali 08", "awalan 08", "prefix tidak valid", "bukan nomor seluler",
                "harus_diawali_08", "nomor_tidak_valid"
        ));

        registerConcept("PHONE_INVALID", Arrays.asList(
                "no handphone tidak valid", "nomor tidak valid", "nomor ponsel tidak valid",
                "nomor telepon tidak valid", "format nomor salah", "invalid phone number",
                "nomor hp tidak valid", "nomor tujuan tidak valid"
        ));

        registerConcept("INSUFFICIENT_BALANCE", Arrays.asList(
                "saldo tidak mencukupi", "saldo tidak cukup", "insufficient balance",
                "saldo anda kurang", "saldo rekening tidak mencukupi", "dana tidak cukup",
                "saldo_tidak_cukup", "saldo_kurang"
        ));

        registerConcept("LIMIT_EXCEEDED", Arrays.asList(
                "transaksi melebihi limit", "melebihi limit harian", "limit per transaksi terlampaui",
                "melebihi limit", "maksimal transaksi tercapai", "limit terlampaui",
                "limit_exceeded", "limit_terlampaui"
        ));

        registerConcept("INVALID_MPIN", Arrays.asList(
                "m-pin salah", "pin salah", "mpin salah", "pin tidak sesuai",
                "m-pin tidak sesuai", "mpin salah 3 kali", "pin terblokir",
                "mpin_salah", "invalid_mpin", "wrong_mpin"
        ));

        registerConcept("ACCOUNT_NOT_FOUND", Arrays.asList(
                "nomor rekening tidak ditemukan", "rekening tujuan tidak ditemukan",
                "rekening tidak terdaftar", "account not found", "cif tidak ditemukan",
                "rekening tidak aktif", "rekening_tidak_ditemukan"
        ));

        registerConcept("SYSTEM_BUSY", Arrays.asList(
                "sistem sedang sibuk", "silakan coba beberapa saat lagi", "transaksi tidak dapat diproses",
                "terjadi gangguan koneksi", "system busy", "timeout", "network error"
        ));

        // Canonical QA Pack Concepts (M-Smile KMP i18n alignment)
        registerConcept("EMPTY_NOMINAL", Arrays.asList(
                "nominal tidak boleh kosong", "masukkan nominal transaksi", "nominal kosong",
                "amount empty", "nominal_tidak_boleh_kosong"
        ));

        registerConcept("EMPTY_PACKAGE", Arrays.asList(
                "jenis paket atau waktu transaksi tidak boleh kosong", "pilih paket terlebih dahulu",
                "package empty", "paket kosong"
        ));

        registerConcept("NO_SOURCE_OF_FUNDS", Arrays.asList(
                "tambahkan dan aktifkan sumber dana", "belum ada sumber dana aktif",
                "tidak ada sumber dana aktif untuk alur itu", "manage source of funds",
                "no source screen", "no source", "atur sumber dana"
        ));

        registerConcept("OPERATOR_UNAVAILABLE", Arrays.asList(
                "operator tidak tersedia mohon coba beberapa saat lagi", "operator gangguan",
                "operator_unavailable", "provider gangguan"
        ));

        registerConcept("SESSION_EXPIRED", Arrays.asList(
                "sesi berakhir", "session expired", "rc 06", "rc 86", "rc 66", "rc 12",
                "silakan login kembali", "session end alert"
        ));

        registerConcept("NETWORK_UNSTABLE", Arrays.asList(
                "yah koneksi internet tidak stabil", "oops koneksi terputus", "cek kembali koneksi anda",
                "koneksi yang aman tidak dapat dilakukan", "ssl pinning failed", "koneksi offline"
        ));

        registerConcept("STATUS_UNKNOWN", Arrays.asList(
                "status transaksi belum diketahui", "transaction status unknown", "rc 99", "qr68",
                "status belum diketahui", "status_unknown"
        ));
    }

    private LocaraIntentMatcher() {}

    private static void registerConcept(String conceptKey, List<String> terms) {
        List<String> normalizedList = new ArrayList<>();
        for (String term : terms) {
            normalizedList.add(normalize(term));
        }
        CONCEPT_CLUSTERS.put(conceptKey, Collections.unmodifiableList(normalizedList));
    }

    /**
     * Calculates the semantic intent match score between an actual text and an expected intent/error code.
     * Range: 0.0 to 1.0.
     */
    public static double calculateIntentScore(String actualText, String expectedIntent) {
        if (actualText == null || expectedIntent == null) return 0.0;

        String actualNorm = normalize(actualText);
        String expectedNorm = normalize(expectedIntent);

        if (actualNorm.isEmpty() || expectedNorm.isEmpty()) return 0.0;

        // 1. Direct or Substring match
        if (actualNorm.equals(expectedNorm)) return 1.0;
        if (actualNorm.contains(expectedNorm) || expectedNorm.contains(actualNorm)) return 0.95;

        // 2. Concept cluster match
        for (Map.Entry<String, List<String>> entry : CONCEPT_CLUSTERS.entrySet()) {
            String clusterKeyNorm = normalize(entry.getKey());
            List<String> terms = entry.getValue();
            boolean actualInConcept = isInCluster(actualNorm, terms) || actualNorm.equals(clusterKeyNorm);
            boolean expectedInConcept = isInCluster(expectedNorm, terms) || expectedNorm.equals(clusterKeyNorm);

            if (actualInConcept && expectedInConcept) {
                return 0.92; // Strong semantic equivalence
            }
        }

        // 3. Token overlap and string similarity
        double tokenScore = StringSimilarity.tokenOverlap(actualNorm, expectedNorm);
        if (tokenScore >= 0.60) {
            return Math.min(1.0, 0.70 + (tokenScore * 0.25));
        }

        double composite = StringSimilarity.compositeScore(actualNorm, expectedNorm);
        return composite;
    }

    private static boolean isInCluster(String text, List<String> clusterTerms) {
        Set<String> textWords = new HashSet<>(Arrays.asList(text.split("\\s+")));
        for (String term : clusterTerms) {
            if (text.equals(term) || text.contains(term) || term.contains(text)) {
                return true;
            }
            // Check term word inclusion ratio: does the sentence contain the cluster term's keywords?
            String[] termWords = term.split("\\s+");
            if (termWords.length > 0) {
                int matchedWords = 0;
                for (String tw : termWords) {
                    if (textWords.contains(tw)) {
                        matchedWords++;
                    }
                }
                double inclusion = (double) matchedWords / termWords.length;
                if (inclusion >= 0.75 && matchedWords >= 2) {
                    return true;
                }
            }
            if (StringSimilarity.tokenOverlap(text, term) >= 0.60) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if actualText satisfies any of the expected error codes / intents above the threshold.
     */
    public static boolean matchesAnyIntent(String actualText, Collection<String> expectedIntents, double threshold) {
        if (actualText == null || expectedIntents == null || expectedIntents.isEmpty()) return false;
        for (String expected : expectedIntents) {
            if (calculateIntentScore(actualText, expected) >= threshold) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates a collection of on-screen texts against a collection of expected intents.
     * Returns IntentMatchResult containing best match details.
     */
    public static IntentMatchResult evaluateScreenIntents(Collection<String> onScreenTexts,
                                                         Collection<String> expectedIntents,
                                                         double threshold) {
        if (onScreenTexts == null || expectedIntents == null || onScreenTexts.isEmpty() || expectedIntents.isEmpty()) {
            return IntentMatchResult.noMatch();
        }

        double bestScore = 0.0;
        String bestActual = null;
        String bestExpected = null;

        for (String actual : onScreenTexts) {
            if (actual == null || actual.trim().isEmpty()) continue;
            for (String expected : expectedIntents) {
                if (expected == null || expected.trim().isEmpty()) continue;

                double score = calculateIntentScore(actual, expected);
                if (score > bestScore) {
                    bestScore = score;
                    bestActual = actual;
                    bestExpected = expected;
                }
            }
        }

        if (bestScore >= threshold) {
            return IntentMatchResult.match(bestActual, bestExpected, bestScore);
        }

        return IntentMatchResult.noMatch();
    }

    private static String normalize(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static class IntentMatchResult {
        private final boolean matched;
        private final String actualText;
        private final String expectedIntent;
        private final double confidence;

        private IntentMatchResult(boolean matched, String actualText, String expectedIntent, double confidence) {
            this.matched = matched;
            this.actualText = actualText;
            this.expectedIntent = expectedIntent;
            this.confidence = confidence;
        }

        public static IntentMatchResult match(String actual, String expected, double score) {
            return new IntentMatchResult(true, actual, expected, score);
        }

        public static IntentMatchResult noMatch() {
            return new IntentMatchResult(false, null, null, 0.0);
        }

        public boolean isMatched() { return matched; }
        public String getActualText() { return actualText; }
        public String getExpectedIntent() { return expectedIntent; }
        public double getConfidence() { return confidence; }

        @Override
        public String toString() {
            if (!matched) return "IntentMatchResult[NO_MATCH]";
            return String.format(Locale.US, "IntentMatchResult[MATCH: '%s' <-> '%s', Score: %.1f%%]",
                    actualText, expectedIntent, confidence * 100.0);
        }
    }
}
