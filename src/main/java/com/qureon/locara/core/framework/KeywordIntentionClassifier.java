package com.qureon.locara.core.framework;

/**
 * Classifies mobile automation keywords into semantic intention tiers.
 * Enables Locara to distinguish between user interactions, strict verification checkpoints,
 * and conditional non-fatal status probes (such as Katalon 11.7 Mobile.isElement*).
 */
public class KeywordIntentionClassifier {

    public enum IntentionTier {
        /**
         * Mutative user interaction (e.g., tap, setText, click, scroll).
         * Failure requires full healing and physical retry on screen.
         */
        INTERACTION,

        /**
         * Strict verification or assertion checkpoint (e.g., verifyElementPresent, assertElementVisible, verifyElementText).
         * Failure requires finding new element candidate and validating assertions against it.
         */
        CHECKPOINT,

        /**
         * Conditional state probe or non-fatal check (e.g., isElementPresent, isElementVisible, waitForElementPresent with OPTIONAL).
         * Used for branching logic. Locara performs non-destructive heuristic lookup without throwing test errors.
         */
        CONDITIONAL_PROBE
    }

    private KeywordIntentionClassifier() {}

    /**
     * Determines the intention tier from a keyword action name or Katalon/Appium method name.
     */
    /**
     * Determines the intention tier from a keyword action name or Katalon/Appium method name.
     */
    public static IntentionTier classify(String actionOrKeyword) {
        if (actionOrKeyword == null || actionOrKeyword.trim().isEmpty()) {
            return IntentionTier.INTERACTION;
        }

        String name = actionOrKeyword.trim().toLowerCase();

        // Strip WebUI. or Mobile. or Windows. qualifiers if present
        if (name.startsWith("webui.") || name.startsWith("mobile.") || name.startsWith("windows.")) {
            name = name.substring(name.indexOf('.') + 1);
        }

        // 1. Conditional status probes (Katalon 11.2 - 11.7+ & Appium non-fatal probes)
        // 26 new WebUI keywords return boolean true/false for branching without failing test runs
        if (name.startsWith("iselement") || name.startsWith("is_element") ||
                name.contains("findfirst") || name.contains("find_first") ||
                name.contains("probe") || name.contains("optional") ||
                name.startsWith("waitfor") && name.contains("optional")) {
            return IntentionTier.CONDITIONAL_PROBE;
        }

        // 2. Strict Checkpoints and Assertions (Katalon assertElement* & verifyElement*)
        // 26 assertElement* keywords fail the step when element is not in expected state
        if (name.startsWith("verify") || name.startsWith("assert") ||
                name.contains("check_point") || name.contains("checkpoint")) {
            return IntentionTier.CHECKPOINT;
        }

        // 3. User interactions (default: tap, setText, click, scroll, clearText, etc.)
        return IntentionTier.INTERACTION;
    }

    /**
     * Checks if the keyword action is a user interaction.
     */
    public static boolean isInteraction(String actionOrKeyword) {
        return classify(actionOrKeyword) == IntentionTier.INTERACTION;
    }

    /**
     * Checks if the keyword action is a non-fatal conditional probe.
     */
    public static boolean isConditionalProbe(String actionOrKeyword) {
        return classify(actionOrKeyword) == IntentionTier.CONDITIONAL_PROBE;
    }

    /**
     * Checks if the keyword action is a verification or assertion checkpoint.
     */
    public static boolean isCheckpoint(String actionOrKeyword) {
        return classify(actionOrKeyword) == IntentionTier.CHECKPOINT;
    }
}
