package com.qureon.locara.core.matcher;

import java.util.*;

/**
 * Universal Mobile UI Semantic Synonym Tensor (Micro-AI Local Heuristic Engine).
 *
 * Provides 100% offline, zero-network, sub-1ms semantic equivalence resolution
 * between English and Indonesian mobile UI terminologies across fintech, e-commerce,
 * ride-hailing, enterprise, and social applications.
 */
public final class UniversalSemanticDictionary {

    private static final Map<String, Set<Integer>> WORD_TO_CLUSTERS = new HashMap<>();
    private static final List<Set<String>> CLUSTERS = new ArrayList<>();
    private static int maxPhraseTokens = 2;
    private static final java.util.regex.Pattern ALPHA_NUM_TOKEN_PATTERN = java.util.regex.Pattern.compile("[a-z0-9]+");

    static {
        // Universal Concept 0: Next / Forward / Progression
        registerCluster("lanjut", "lanjutkan", "next", "continue", "berikutnya", "proceed", "selanjutnya", "forward");

        // Universal Concept 1: Confirmation / General Affirmative Action
        registerCluster("konfirmasi", "confirm", "ok", "oke", "setuju", "agree", "ya", "yes", "siap", "mengerti", "paham");

        // Universal Concept 15: Financial Payment / Transaction Execution (Strictly Isolated)
        registerCluster("bayar", "bayarkan", "pay", "payment", "checkout", "transfer", "kirim uang", "top up sekarang");

        // Universal Concept 16: Task Completion / Done
        registerCluster("selesai", "done", "finish", "beres", "tuntas");

        // Universal Concept 17: Submission / Dispatch
        registerCluster("kirim", "send", "submit", "proses", "process", "ajukan", "apply");

        // Universal Concept 2: Authentication / Sign In
        registerCluster("masuk", "login", "sign in", "signin", "log in", "logon");

        // Universal Concept 3: Registration / Sign Up
        registerCluster("daftar", "register", "registrasi", "signup", "sign up", "buat akun", "create account");

        // Universal Concept 4: Cancel / Dismiss / Return
        registerCluster("batal", "batalkan", "cancel", "kembali", "back", "close", "tutup", "keluar", "exit", "dismiss", "skip", "lewati");

        // Universal Concept 5: Search / Query
        registerCluster("cari", "search", "find", "temukan", "filter", "saring");

        // Universal Concept 6: Save / Store
        registerCluster("simpan", "save", "unduh", "download", "keep");

        // Universal Concept 7: Delete / Remove
        registerCluster("hapus", "delete", "remove", "buang", "clear", "bersihkan");

        // Universal Concept 8: Top Up / Reload
        registerCluster("topup", "top up", "isi ulang", "reload", "recharge");

        // Universal Concept 9: Check / Verify / Inspect
        registerCluster("cek", "check", "periksa", "verify", "lihat", "view", "detail", "rincian");

        // Universal Concept 10: Profile / User
        registerCluster("akun", "account", "profil", "profile", "saya", "me", "pengguna", "user");

        // Universal Concept 11: Settings
        registerCluster("pengaturan", "settings", "setting", "opsi", "options", "preferensi", "preferences");

        // Universal Concept 12: Notification / Inbox
        registerCluster("notifikasi", "notification", "pesan", "inbox", "pemberitahuan", "alerts");

        // Universal Concept 13: History / Activity
        registerCluster("riwayat", "history", "aktivitas", "activity", "mutasi", "catatan", "records");

        // Universal Concept 14: Refresh / Reload
        registerCluster("muat ulang", "refresh", "reload", "sinkron", "sync");

        // Universal Concept 18: Investment Subscription / Buy / Invest
        registerCluster("subscribe", "invest", "investasi", "berinvestasi", "pembelian reksa dana", "beli reksadana", "beli unit");

        // Universal Concept 19: Investment Redemption / Sell / Cash Out
        registerCluster("redeem", "redemption", "cairkan", "pencairan", "jual unit", "tarik dana", "cash out");

        // Universal Concept 20: Performance / Yield / Return
        registerCluster("kinerja", "performance", "return", "imbal hasil", "yield", "histori kinerja", "grafik");

        // Universal Concept 21: Prospectus / Documents / Legal & FFS
        registerCluster("dokumen", "document", "prospektus", "prospectus", "fund fact sheet", "ffs", "ringkasan produk", "dokumen penawaran");

        // Universal Concept 22: Description / About / Product Info
        registerCluster("deskripsi", "description", "tentang", "about", "detail produk", "informasi produk", "profil produk");

        // Universal Concept 23: Comparison / Benchmark
        registerCluster("bandingkan", "compare", "komparasi", "comparison", "tolok ukur", "benchmark");

        // Universal Concept 24: Asset Class / Product Categories
        registerCluster("pasar uang", "money market", "pendapatan tetap", "fixed income", "campuran", "balanced", "saham", "equity", "obligasi", "bonds", "sbn ritel", "sukuk");

        // Universal Concept 25: Risk Profile / Assessment
        registerCluster("profil risiko", "risk profile", "sid", "kuesioner risiko", "toleransi risiko");
    }

    private static void registerCluster(String... words) {
        int clusterId = CLUSTERS.size();
        Set<String> cluster = new HashSet<>();
        for (String w : words) {
            String clean = StringSimilarity.normalize(w);
            if (!clean.isEmpty()) {
                cluster.add(clean);
                WORD_TO_CLUSTERS.computeIfAbsent(clean, k -> new HashSet<>()).add(clusterId);
                String[] rawWords = w.trim().toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
                int nonEmptyCount = 0;
                for (String rw : rawWords) {
                    if (!rw.isEmpty()) nonEmptyCount++;
                }
                if (nonEmptyCount > maxPhraseTokens) {
                    maxPhraseTokens = nonEmptyCount;
                }
            }
        }
        CLUSTERS.add(Collections.unmodifiableSet(cluster));
    }

    private UniversalSemanticDictionary() {}

    /**
     * Checks if wordA and wordB are semantically equivalent synonyms.
     */
    public static boolean isSynonym(String wordA, String wordB) {
        if (wordA == null || wordB == null) return false;
        String normA = StringSimilarity.normalize(wordA);
        String normB = StringSimilarity.normalize(wordB);
        if (normA.isEmpty() || normB.isEmpty()) return false;
        if (normA.equals(normB)) return true;

        Set<Integer> clustersA = WORD_TO_CLUSTERS.get(normA);
        Set<Integer> clustersB = WORD_TO_CLUSTERS.get(normB);

        return clustersA != null && clustersB != null && !Collections.disjoint(clustersA, clustersB);
    }

    private static final Set<String> UI_WIDGET_TOKENS = new HashSet<>(Arrays.asList(
            "btn", "button", "txt", "text", "lbl", "label", "input", "img", "icon", "view", "group", "item"
    ));

    /**
     * Calculates the semantic affinity score between two phrases (0.0 to 1.0).
     * Rewards exact matches and cross-lingual synonym matches across both single words
     * and multi-word concept phrases (e.g. "Pilih Pasar Uang" vs "Money Market Tab").
     */
    public static double calculateSemanticAffinity(String phraseA, String phraseB) {
        if (phraseA == null || phraseB == null) return 0.0;
        if (isSynonym(phraseA, phraseB)) {
            return 1.0;
        }

        List<String> unitsA = extractConceptUnits(phraseA);
        List<String> unitsB = extractConceptUnits(phraseB);

        if (unitsA.isEmpty() || unitsB.isEmpty()) return 0.0;

        int matchCount = 0;
        for (String a : unitsA) {
            for (String b : unitsB) {
                if (isSynonym(a, b)) {
                    matchCount++;
                    break;
                }
            }
        }

        int total = Math.max(unitsA.size(), unitsB.size());
        return (double) matchCount / total;
    }

    private static List<String> extractConceptUnits(String phrase) {
        List<String> raw = new ArrayList<>();
        java.util.regex.Matcher m = ALPHA_NUM_TOKEN_PATTERN.matcher(phrase.toLowerCase(Locale.ROOT));
        while (m.find()) {
            raw.add(m.group());
        }
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> nonWidget = new ArrayList<>();
        for (String t : raw) {
            if (!UI_WIDGET_TOKENS.contains(t)) {
                nonWidget.add(t);
            }
        }
        List<String> base = nonWidget.isEmpty() ? raw : nonWidget;

        List<String> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int i = 0;
        int limit = Math.max(2, maxPhraseTokens);
        while (i < base.size()) {
            String matchedPhrase = null;
            int matchedLen = 1;
            int maxWindow = Math.min(limit, base.size() - i);
            for (int len = maxWindow; len >= 2; len--) {
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < len; k++) {
                    sb.append(base.get(i + k));
                }
                String candidate = sb.toString();
                if (WORD_TO_CLUSTERS.containsKey(candidate)) {
                    matchedPhrase = candidate;
                    matchedLen = len;
                    break;
                }
            }
            String unit = matchedPhrase != null ? matchedPhrase : base.get(i);
            if (seen.add(unit)) {
                merged.add(unit);
            }
            i += matchedLen;
        }
        return merged;
    }

    private static final Set<String> DOMAIN_ACRONYMS = new HashSet<>(Arrays.asList(
            "qris", "rtgs", "bi-fast", "bifast", "skn", "mpin", "pin", "cif", "va", "otp", "kmp", "cusip", "xbrl", "sbn"
    ));

    private static final Map<String, String> CRYPTIC_ABBREVIATIONS = new HashMap<>();
    static {
        CRYPTIC_ABBREVIATIONS.put("rek", "rekening");
        CRYPTIC_ABBREVIATIONS.put("trx", "transaksi");
        CRYPTIC_ABBREVIATIONS.put("no", "nomor");
        CRYPTIC_ABBREVIATIONS.put("usr", "user");
        CRYPTIC_ABBREVIATIONS.put("mgr", "manager");
        CRYPTIC_ABBREVIATIONS.put("cfg", "config");
        CRYPTIC_ABBREVIATIONS.put("proc", "process");
        CRYPTIC_ABBREVIATIONS.put("acc", "account");
        CRYPTIC_ABBREVIATIONS.put("bal", "balance");
        CRYPTIC_ABBREVIATIONS.put("dest", "destination");
        CRYPTIC_ABBREVIATIONS.put("src", "source");
    }

    /**
     * Checks if a token is an established domain acronym that must be strictly preserved.
     */
    public static boolean isEstablishedDomainAcronym(String token) {
        if (token == null) return false;
        return DOMAIN_ACRONYMS.contains(token.trim().toLowerCase());
    }

    /**
     * Checks if a token is a known cryptic abbreviation and returns its expanded canonical form.
     */
    public static String expandCrypticAbbreviation(String token) {
        if (token == null) return null;
        return CRYPTIC_ABBREVIATIONS.get(token.trim().toLowerCase());
    }
}
