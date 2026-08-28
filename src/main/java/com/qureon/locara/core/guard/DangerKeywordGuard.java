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
            "hapus", "delete", "remove", "batal", "cancel", "tutup", "close",
            "keluar", "exit", "logout", "blokir", "block", "tolak", "reject", "reset"
    )));

    private static final Set<String> POSITIVE_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "lanjut", "next", "continue", "bayar", "pay", "kirim", "send",
            "proses", "process", "submit", "konfirmasi", "confirm", "setuju",
            "agree", "ok", "ya", "yes", "simpan", "save",
            "transfer", "topup", "debet", "debit", "tarik", "beli", "buy"
    )));

    private DangerKeywordGuard() {}

    /**
     * Checks if selecting the candidate node violates polarity safety rules with respect to targetHint.
     * @return true if there is a dangerous polarity mismatch (MUST REJECT).
     */
    public static boolean isPolarityViolation(String targetHint, UIElementNode candidateNode) {
        if (targetHint == null || candidateNode == null) return false;

        Set<String> targetTokens = StringSimilarity.tokenize(targetHint);

        boolean targetIsPositive = containsAny(targetTokens, POSITIVE_KEYWORDS);
        boolean targetIsDanger = containsAny(targetTokens, DANGER_KEYWORDS);

        Set<String> nodeTokens = extractNodeTokens(candidateNode);
        boolean nodeHasDanger = containsAny(nodeTokens, DANGER_KEYWORDS);
        boolean nodeHasPositive = containsAny(nodeTokens, POSITIVE_KEYWORDS);

        // If candidate shares danger intention when target explicitly asks for danger: safe!
        if (targetIsDanger && nodeHasDanger) {
            return false;
        }

        // If candidate shares positive intention and target does not ask for danger: safe!
        if (targetIsPositive && nodeHasPositive && !targetIsDanger) {
            return false;
        }

        // Case 1: Target wants purely positive action ('Lanjut'/'Bayar'/'Transfer'), but candidate contains danger ('Batal'/'Hapus')
        if (targetIsPositive && !targetIsDanger && nodeHasDanger) {
            return true;
        }

        // Case 2: Target wants purely danger action ('Batal'/'Cancel'), but candidate contains positive ('Lanjut'/'Bayar'/'Transfer')
        if (targetIsDanger && !targetIsPositive && nodeHasPositive) {
            return true;
        }

        // Case 3 (Bank Mega Zero-Loss Invariant): Target is neutral/non-danger (e.g. 'Lihat Rekening', 'Cek Saldo'),
        // but candidate is destructive ('Blokir', 'Hapus', 'Tolak', 'Batal').
        // NEVER allow a neutral target to match a destructive/danger element!
        if (!targetIsDanger && nodeHasDanger) {
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
        set.addAll(StringSimilarity.tokenize(node.getStrippedResourceId()));
        set.addAll(StringSimilarity.tokenize(node.getContentDesc()));
        return set;
    }

    private static boolean containsAny(Set<String> sourceTokens, Set<String> dictionary) {
        for (String token : sourceTokens) {
            for (String dictWord : dictionary) {
                if (token.equals(dictWord)) {
                    return true;
                }
                if (dictWord.length() >= 4 && (token.startsWith(dictWord) || token.contains(dictWord))) {
                    return true;
                }
            }
        }
        return false;
    }
}
