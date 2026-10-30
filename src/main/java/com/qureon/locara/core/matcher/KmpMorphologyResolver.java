package com.qureon.locara.core.matcher;

import com.qureon.locara.core.model.UIElementNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Universal KMP (Kotlin Multiplatform / Compose Multiplatform) and Appium testID Morphology Resolver.
 * Decodes standard <screen>_<element> naming taxonomy, dynamic token wildcards, action-suffix affinity,
 * and scrambled PIN pad invariants.
 */
public final class KmpMorphologyResolver {

    // Action-specific suffix affinities
    private static final Set<String> TAP_SUFFIXES = new HashSet<>(Arrays.asList(
            "_button", "_tap", "_item", "_card", "_row", "_radio", "_checkbox",
            "_back_button", "_close_button", "_scrim", "_switch", "_toggle"
    ));

    private static final Set<String> INPUT_SUFFIXES = new HashSet<>(Arrays.asList(
            "_input", "_edit", "_search", "_field", "_dob_day", "_dob_month", "_dob_year"
    ));

    private static final Set<String> PROBE_SUFFIXES = new HashSet<>(Arrays.asList(
            "_error", "_message", "_value", "_title", "_screen", "_sheet", "_banner",
            "_indicator", "_dots", "_badge", "_header", "_footer"
    ));

    private static final Pattern DYNAMIC_TOKEN_PATTERN = Pattern.compile("<[^>]+>");

    private KmpMorphologyResolver() {}

    /**
     * Calculates the morphological affinity score (0.0 to 1.0) between target hint and candidate node.
     */
    public static double calculateMorphologyScore(UIElementNode node, String targetHint, String action) {
        if (node == null || targetHint == null || targetHint.trim().isEmpty()) {
            return 0.0;
        }

        String rawResId = node.getStrippedResourceId();
        if (rawResId == null || rawResId.trim().isEmpty()) {
            rawResId = node.getContentDesc();
        }
        if (rawResId == null || rawResId.trim().isEmpty()) {
            return 0.0;
        }

        String rawResIdLower = rawResId.toLowerCase(Locale.ROOT);
        String targetLower = targetHint.toLowerCase(Locale.ROOT);

        // 1. Exact or Direct Normalized Match
        if (rawResIdLower.equals(targetLower)) {
            return 1.0;
        }

        // 2. Scrambled PIN Key Resolution (e.g., target hint 'pin_key_5' or digit '5' matching 'pin_key_5')
        if (isPinKeyMatch(rawResIdLower, targetLower)) {
            return 0.98;
        }

        // 3. Dynamic Token Wildcard Resolution (e.g. 'transfer_amount_key_<key>' <-> 'transfer_amount_key_5')
        if (matchesDynamicToken(rawResIdLower, targetLower)) {
            return 0.95;
        }

        // 4. Action-Affinity Suffix Scoring
        double affinityBonus = 0.0;
        if ("TAP".equalsIgnoreCase(action)) {
            if (hasAnySuffix(rawResIdLower, TAP_SUFFIXES)) {
                affinityBonus += 0.20;
            }
        } else if ("SET_TEXT".equalsIgnoreCase(action)) {
            if (hasAnySuffix(rawResIdLower, INPUT_SUFFIXES)) {
                affinityBonus += 0.25;
            }
        } else if ("VERIFY_PRESENT".equalsIgnoreCase(action) || "VERIFY_VISIBLE".equalsIgnoreCase(action)) {
            if (hasAnySuffix(rawResIdLower, PROBE_SUFFIXES)) {
                affinityBonus += 0.20;
            }
        }

        // 5. Composite semantic similarity and token overlap between screen and element
        String targetSpaced = normalizeForTokens(targetHint);
        String candSpaced = normalizeForTokens(rawResId);
        double simScore = StringSimilarity.compositeScore(candSpaced, targetSpaced);

        return Math.min(1.0, (simScore * 0.80) + affinityBonus);
    }

    /**
     * Evaluates whether candidate is a valid scrambled PIN digit match.
     */
    public static boolean isPinKeyMatch(String candId, String targetHint) {
        if (candId == null || targetHint == null) return false;

        // Pattern: pin_key_<digit>
        if (candId.startsWith("pin_key_") || candId.startsWith("pin_digit_")) {
            String candDigit = candId.replaceAll("[^0-9]", "");
            String targetDigit = targetHint.replaceAll("[^0-9]", "");
            if (!candDigit.isEmpty() && candDigit.equals(targetDigit)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves dynamic template tokens such as <key>, <id>, <code>, <i>, <n>.
     */
    public static boolean matchesDynamicToken(String candId, String targetHint) {
        if (candId == null || targetHint == null) return false;

        String template = targetHint;
        if (!DYNAMIC_TOKEN_PATTERN.matcher(template).find()) {
            if (DYNAMIC_TOKEN_PATTERN.matcher(candId).find()) {
                template = candId;
                candId = targetHint;
            } else {
                return false;
            }
        }

        // Convert <key> into regex [a-zA-Z0-9_-]+
        String regex = "^" + DYNAMIC_TOKEN_PATTERN.matcher(template).replaceAll("[a-zA-Z0-9_-]+") + "$";
        try {
            return Pattern.compile(regex).matcher(candId).matches();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasAnySuffix(String text, Set<String> suffixes) {
        for (String suffix : suffixes) {
            if (text.endsWith(suffix) || text.contains(suffix + "_")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeForTokens(String input) {
        if (input == null) return "";
        return input.toLowerCase(Locale.ROOT)
                .replace("com.bankmega.msmile:id/", "")
                .replace("id/", "")
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
