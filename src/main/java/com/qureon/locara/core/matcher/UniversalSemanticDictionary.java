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

    private static final Map<String, Integer> WORD_TO_CLUSTER = new HashMap<>();
    private static final List<Set<String>> CLUSTERS = new ArrayList<>();

    static {
        // Universal Concept 0: Next / Forward / Progression
        registerCluster("lanjut", "lanjutkan", "next", "continue", "berikutnya", "proceed", "selanjutnya", "forward");

        // Universal Concept 1: Confirmation / Submit / Action Execution
        registerCluster("konfirmasi", "confirm", "kirim", "send", "submit", "bayar", "pay", "checkout", "proses", "process", "selesai", "done", "finish", "ok", "setuju", "agree");

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
    }

    private static void registerCluster(String... words) {
        int clusterId = CLUSTERS.size();
        Set<String> cluster = new HashSet<>();
        for (String w : words) {
            String clean = StringSimilarity.normalize(w);
            if (!clean.isEmpty()) {
                cluster.add(clean);
                WORD_TO_CLUSTER.put(clean, clusterId);
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

        Integer clusterA = WORD_TO_CLUSTER.get(normA);
        Integer clusterB = WORD_TO_CLUSTER.get(normB);

        return clusterA != null && clusterA.equals(clusterB);
    }

    private static final Set<String> UI_WIDGET_TOKENS = new HashSet<>(Arrays.asList(
            "btn", "button", "txt", "text", "lbl", "label", "input", "img", "icon", "view", "group", "item"
    ));

    /**
     * Calculates the semantic affinity score between two phrases (0.0 to 1.0).
     * Rewards exact matches and cross-lingual synonym matches.
     */
    public static double calculateSemanticAffinity(String phraseA, String phraseB) {
        if (phraseA == null || phraseB == null) return 0.0;
        Set<String> rawTokensA = StringSimilarity.tokenize(phraseA);
        Set<String> rawTokensB = StringSimilarity.tokenize(phraseB);

        Set<String> tokensA = filterWidgetTokens(rawTokensA);
        Set<String> tokensB = filterWidgetTokens(rawTokensB);

        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0;

        int matchCount = 0;
        for (String a : tokensA) {
            for (String b : tokensB) {
                if (isSynonym(a, b)) {
                    matchCount++;
                    break;
                }
            }
        }

        int total = Math.max(tokensA.size(), tokensB.size());
        return (double) matchCount / total;
    }

    private static Set<String> filterWidgetTokens(Set<String> tokens) {
        Set<String> filtered = new HashSet<>();
        for (String t : tokens) {
            if (!UI_WIDGET_TOKENS.contains(t)) {
                filtered.add(t);
            }
        }
        return filtered.isEmpty() ? tokens : filtered;
    }
}
