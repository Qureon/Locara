package com.qureon.locara.playwright;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.logging.LocaraLogger;
import com.qureon.locara.core.model.AppTarget;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;

import java.util.Locale;

/**
 * First-Class Standalone Playwright Self-Healing & Autonomous Web Recovery API.
 */
public class PlaywrightLocara {

    private static final String LOG_PREFIX = "[PlaywrightLocara]";
    private static final int MIN_PROBE_TIMEOUT_MS = 500;
    private static final int MIN_ACTION_TIMEOUT_MS = 5000;

    private static final LocaraEngine ENGINE = new LocaraEngine();
    private static volatile int defaultProbeTimeoutMs = 2500;

    static {
        ENGINE.getConfig().setAppTarget(AppTarget.WEB);
    }

    public static LocaraEngine getEngine() { return ENGINE; }

    public static void setDefaultProbeTimeoutMs(int timeoutMs) {
        defaultProbeTimeoutMs = Math.max(MIN_PROBE_TIMEOUT_MS, timeoutMs);
    }

    public static int getDefaultProbeTimeoutMs() { return defaultProbeTimeoutMs; }

    public static boolean click(Object page, String selectorOrXPath, String targetHint) {
        return click(page, selectorOrXPath, targetHint, defaultProbeTimeoutMs);
    }

    public static boolean click(Object page, String selectorOrXPath, String targetHint, int probeTimeoutMs) {
        requirePage(page);
        try {
            Object locator = PlaywrightReflectionBridge.findLocator(page, selectorOrXPath);
            if (locator != null && PlaywrightReflectionBridge.invokeClick(locator, probeTimeoutMs)) {
                return true;
            }
        } catch (Throwable t) {
            LocaraLogger.info(LOG_PREFIX, "Standard locator failed: '" + selectorOrXPath + "' -> Initiating autonomous healing...");
        }
        return ENGINE.getConfig().isEnabled() && executeHealing(page, selectorOrXPath, targetHint, "CLICK", null, probeTimeoutMs);
    }

    public static boolean fill(Object page, String selectorOrXPath, String text, String targetHint) {
        return fill(page, selectorOrXPath, text, targetHint, defaultProbeTimeoutMs);
    }

    public static boolean fill(Object page, String selectorOrXPath, String text, String targetHint, int probeTimeoutMs) {
        requirePage(page);
        try {
            Object locator = PlaywrightReflectionBridge.findLocator(page, selectorOrXPath);
            if (locator != null && PlaywrightReflectionBridge.invokeFill(locator, text, probeTimeoutMs)) {
                return true;
            }
        } catch (Throwable t) {
            LocaraLogger.info(LOG_PREFIX, "Standard locator failed: '" + selectorOrXPath + "' -> Initiating autonomous healing...");
        }
        return ENGINE.getConfig().isEnabled() && executeHealing(page, selectorOrXPath, targetHint, "FILL", text, probeTimeoutMs);
    }

    public static String textContent(Object page, String selectorOrXPath, String targetHint) {
        requirePage(page);
        try {
            Object locator = PlaywrightReflectionBridge.findLocator(page, selectorOrXPath);
            if (locator != null) {
                Object result = PlaywrightReflectionBridge.invokeTextContent(locator, defaultProbeTimeoutMs);
                if (result != null) return result.toString();
            }
        } catch (Throwable ignored) {}

        String healedSelector = healSelector(page, selectorOrXPath, targetHint, "GET_TEXT");
        if (healedSelector != null) {
            try {
                Object locator = PlaywrightReflectionBridge.findLocator(page, healedSelector);
                if (locator != null) {
                    Object res = PlaywrightReflectionBridge.invokeTextContent(locator, Math.max(MIN_ACTION_TIMEOUT_MS, defaultProbeTimeoutMs));
                    return res != null ? res.toString() : "";
                }
            } catch (Throwable t) {
                LocaraLogger.error(LOG_PREFIX, "Failed to get textContent from healed locator: " + t.getMessage());
            }
        }
        return null;
    }

    public static boolean isVisible(Object page, String selectorOrXPath, String targetHint) {
        if (page == null) return false;
        Object directLoc = PlaywrightReflectionBridge.findLocator(page, selectorOrXPath);
        if (directLoc != null && PlaywrightReflectionBridge.invokeIsVisible(directLoc)) return true;

        String healedSelector = healSelector(page, selectorOrXPath, targetHint, "VISIBLE");
        if (healedSelector != null) {
            Object healedLoc = PlaywrightReflectionBridge.findLocator(page, healedSelector);
            return healedLoc != null && PlaywrightReflectionBridge.invokeIsVisible(healedLoc);
        }
        return false;
    }

    public static Object locator(Object page, String selectorOrXPath, String targetHint) {
        if (page == null) return null;
        Object loc = PlaywrightReflectionBridge.findLocator(page, selectorOrXPath);
        if (loc != null && PlaywrightReflectionBridge.invokeCount(loc) > 0) return loc;

        String healed = healSelector(page, selectorOrXPath, targetHint, "LOCATOR");
        return healed != null ? PlaywrightReflectionBridge.findLocator(page, healed)
                : PlaywrightReflectionBridge.findLocator(page, selectorOrXPath);
    }

    public static String healSelector(Object page, String originalSelector, String targetHint, String action) {
        HealResult result = runHealing(page, originalSelector, targetHint, action);
        if (result != null && result.isSuccess() && result.getCandidate() != null) {
            return toPlaywrightSelector(result.getCandidate().getSuggestedXPath());
        }
        return null;
    }

    private static boolean executeHealing(Object page, String originalSelector, String targetHint,
                                          String action, String textArg, int probeTimeoutMs) {
        HealResult result = runHealing(page, originalSelector, targetHint, action);
        if (result == null) return false;
        if (!result.isSuccess() || result.getCandidate() == null) {
            LocaraLogger.error(LOG_PREFIX, "Healing FAILED for hint '" + targetHint + "': " + result.getMessage());
            return false;
        }

        String healedXPath = result.getCandidate().getSuggestedXPath();
        LocaraLogger.info(LOG_PREFIX, String.format(Locale.US, "SUCCESS: Healed '%s' -> '%s' (Confidence: %.1f%%)",
                originalSelector, healedXPath, result.getCandidate().getConfidenceScore() * 100.0));

        String playwrightSelector = toPlaywrightSelector(healedXPath);
        int actionTimeoutMs = Math.max(MIN_ACTION_TIMEOUT_MS, probeTimeoutMs);

        try {
            Object healedLocator = PlaywrightReflectionBridge.findLocator(page, playwrightSelector);
            if (healedLocator == null) healedLocator = PlaywrightReflectionBridge.findLocator(page, healedXPath);
            if (healedLocator != null) {
                return "FILL".equalsIgnoreCase(action)
                        ? PlaywrightReflectionBridge.invokeFill(healedLocator, textArg != null ? textArg : "", actionTimeoutMs)
                        : PlaywrightReflectionBridge.invokeClick(healedLocator, actionTimeoutMs);
            }
        } catch (Throwable t) {
            LocaraLogger.error(LOG_PREFIX, "Error executing action on healed locator: " + t.getMessage());
        }
        return false;
    }

    private static HealResult runHealing(Object page, String originalSelector, String targetHint, String action) {
        if (!ENGINE.getConfig().isEnabled()) return null;
        String htmlContent = PlaywrightReflectionBridge.extractHtmlContent(page);
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            LocaraLogger.error(LOG_PREFIX, "Error: Page HTML DOM is empty or inaccessible!");
            return null;
        }
        HealContext context = HealContext.builder()
                .targetHint(targetHint)
                .action(action)
                .originalLocator(originalSelector)
                .screenName(PlaywrightReflectionBridge.extractPageTitleOrUrl(page))
                .build();
        return ENGINE.heal(htmlContent, context);
    }

    private static String toPlaywrightSelector(String xpath) {
        if (xpath == null || xpath.isEmpty()) return "";
        if (xpath.startsWith("xpath=")) return xpath;
        if (xpath.startsWith("//") || xpath.startsWith("/") || xpath.startsWith("(/")) return "xpath=" + xpath;
        return xpath;
    }

    private static void requirePage(Object page) {
        if (page == null) throw new IllegalArgumentException("[PlaywrightLocara] Page instance cannot be null!");
    }
}
