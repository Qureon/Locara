package com.qureon.locara.core.guard;

import com.qureon.locara.core.matcher.StringSimilarity;
import com.qureon.locara.core.model.UIElementNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Enterprise safety guard preventing accidental clicks on dangerous or contradictory buttons
 * (e.g., clicking 'Batal' or 'Hapus' when looking for 'Lanjut').
 */
public final class DangerKeywordGuard {

    private static final Set<String> DANGER_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "hapus", "hapuskan", "penghapusan", "menghapus", "delete", "remove",
            "batal", "batalkan", "pembatalan", "membatalkan", "cancel",
            "tutup", "penutupan", "menutup", "close",
            "keluar", "exit", "logout", "signout",
            "blokir", "pemblokiran", "memblokir", "block", "freeze",
            "tolak", "penolakan", "menolak", "reject", "decline", "reset"
    )));

    private static final Set<String> FINANCIAL_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "bayar", "bayarkan", "pembayaran", "membayar", "pay", "payment", "checkout",
            "transfer", "kirimuang", "kirim uang",
            "beli", "belikan", "pembelian", "membeli", "buy", "purchase",
            "topup", "top up", "debet", "pendebetan", "debit",
            "tarik", "penarikan", "withdraw", "tagihan",
            "subscribe", "subscription", "langganan", "penambahan unit",
            "redeem", "redemption", "cairkan", "pencairan", "jual unit",
            "switching", "pengalihan"
    )));

    private static final Set<String> POSITIVE_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "lanjut", "lanjutkan", "next", "continue", "proceed", "kirim", "send",
            "proses", "process", "submit", "konfirmasi", "confirm", "setuju",
            "agree", "ok", "oke", "ya", "yes", "simpan", "save", "selesai", "done", "finish"
    )));

    private static final Set<String> CORPORATE_PREFIX_TOKENS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "pt", "perseroan", "perusahaan"
    )));

    private static final Set<String> CORPORATE_SUFFIX_TOKENS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "manajemen", "management", "sekuritas", "securities", "tbk"
    )));

    private static final Set<String> WIDGET_PREFIXES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "btn", "button", "ic", "icon", "tv", "txt", "lbl", "label", "et", "input", "img", "iv"
    )));

    private DangerKeywordGuard() {}

    /**
     * Checks if selecting the candidate node violates polarity safety rules with respect to targetHint.
     * @return true if there is a dangerous polarity mismatch (MUST REJECT).
     */
    public static boolean isPolarityViolation(String targetHint, UIElementNode candidateNode) {
        if (targetHint == null || targetHint.isBlank() || candidateNode == null) return false;

        Set<String> targetTokens = StringSimilarity.tokenize(targetHint);
        if (targetTokens.isEmpty()) return false;

        boolean targetIsFinancial = containsAny(targetTokens, FINANCIAL_KEYWORDS);
        boolean targetIsPositive = containsAny(targetTokens, POSITIVE_KEYWORDS) || targetIsFinancial;
        boolean targetIsDanger = containsAny(targetTokens, DANGER_KEYWORDS);

        Set<String> nodeTokens = extractNodeTokens(candidateNode);
        if (nodeTokens.isEmpty()) return false;

        boolean nodeHasFinancial = containsAny(nodeTokens, FINANCIAL_KEYWORDS);
        boolean nodeHasDanger = containsAny(nodeTokens, DANGER_KEYWORDS);
        boolean nodeHasPositive = containsAny(nodeTokens, POSITIVE_KEYWORDS) || nodeHasFinancial;

        // Rule 0 (Strict Bank Mega Financial Isolation Invariant):
        // A financial transaction action (e.g. 'Bayar', 'Transfer', 'Checkout', 'Beli')
        // MUST NEVER be clicked if the target hint did not explicitly request a financial action
        // (e.g. looking for 'OK', 'Lanjut', 'Tutup', 'Simpan', 'Informasi').
        if (nodeHasFinancial && !targetIsFinancial) {
            return true;
        }

        // Rule 0B (Corporate Issuer Header vs Instrument Isolation):
        // If candidate node represents a corporate legal entity (e.g. "PT Manulife Aset Manajemen Indonesia"),
        // but targetHint does NOT explicitly ask for a corporate entity/PT,
        // strictly reject candidate to prevent false-healing product cards to corporate company headers.
        if (isCorporateIssuerNode(candidateNode) && !isCorporateTarget(targetTokens)) {
            return true;
        }

        // Rule 1 (Bank Mega Zero-Loss Destructive Lockout):
        // If target does NOT explicitly request a destructive/danger action,
        // NEVER allow matching any candidate that contains a danger keyword—even if the candidate
        // also contains a positive word (e.g. 'Ya, Hapus', 'Konfirmasi Batal').
        if (!targetIsDanger && nodeHasDanger) {
            return true;
        }

        // If candidate shares danger intention when target explicitly asks for danger: safe!
        if (targetIsDanger && nodeHasDanger) {
            return false;
        }

        // If candidate shares positive intention and target does not ask for danger: safe!
        if (targetIsPositive && nodeHasPositive && !targetIsDanger) {
            return false;
        }

        // Case 2: Target wants purely danger action ('Batal'/'Cancel'), but candidate contains positive ('Lanjut'/'Bayar'/'Transfer')
        if (targetIsDanger && !targetIsPositive && nodeHasPositive) {
            return true;
        }

        return false;
    }

    public static String getViolationMessage(String targetHint, UIElementNode candidateNode) {
        return String.format("[SAFETY LOCKOUT] Polarity mismatch detected! Target hint '%s' conflicts with candidate node [text='%s', id='%s']. Action rejected to prevent unintended financial/state disruption.",
                targetHint, candidateNode.getText(), candidateNode.getStrippedResourceId());
    }

    private static Set<String> extractNodeTokens(UIElementNode node) {
        Set<String> set = new HashSet<>();
        set.addAll(StringSimilarity.tokenize(node.getText()));
        set.addAll(StringSimilarity.tokenize(node.getAggregatedDescendantText(2)));
        set.addAll(StringSimilarity.tokenize(node.getStrippedResourceId()));
        set.addAll(StringSimilarity.tokenize(node.getContentDesc()));
        return set;
    }

    private static boolean containsAny(Set<String> sourceTokens, Set<String> dictionary) {
        for (String token : sourceTokens) {
            if (matchesDictionaryWord(token, dictionary)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesDictionaryWord(String token, Set<String> dictionary) {
        if (token == null || token.isEmpty()) return false;
        if (dictionary.contains(token)) {
            return true;
        }

        // Strip common Indonesian enclitics/imperative suffixes (-kan, -nya, -lah)
        if (token.length() > 5 && (token.endsWith("kan") || token.endsWith("nya") || token.endsWith("lah"))) {
            String stem = token.substring(0, token.length() - 3);
            if (dictionary.contains(stem)) {
                return true;
            }
        }

        // Strip leading widget prefix on unseparated identifier tokens (e.g. "btnhapus" -> "hapus")
        for (String prefix : WIDGET_PREFIXES) {
            if (token.length() > prefix.length() + 2 && token.startsWith(prefix)) {
                String remainder = token.substring(prefix.length());
                if (dictionary.contains(remainder)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isCorporateTarget(Set<String> targetTokens) {
        return containsAny(targetTokens, CORPORATE_PREFIX_TOKENS);
    }

    private static boolean isCorporateIssuerNode(UIElementNode node) {
        if (node == null) return false;
        return isCorporateText(node.getText()) || isCorporateText(node.getContentDesc());
    }

    private static boolean isCorporateText(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String normalized = raw.toLowerCase().trim();
        boolean hasPtPrefix = normalized.startsWith("pt ") || normalized.startsWith("pt.") 
                || normalized.contains(" pt ") || normalized.contains(" perseroan ");
        if (!hasPtPrefix) return false;

        Set<String> tokens = StringSimilarity.tokenize(normalized);
        return containsAny(tokens, CORPORATE_SUFFIX_TOKENS);
    }
}
