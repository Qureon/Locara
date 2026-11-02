package com.qureon.locara.playwright;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.model.AppTarget;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;

import java.lang.reflect.Method;

/**
 * First-Class Standalone Playwright Self-Healing & Autonomous Web Recovery API.
 * Compatible with Microsoft Playwright for Java (`com.microsoft.playwright.Page` and `Locator`),
 * JUnit 5, TestNG, and Cucumber web suites without requiring third-party testing servers.
 *
 * Designed with dynamic reflection dispatch to prevent runtime ClassNotFoundException
 * and maintain zero-lockin across heterogeneous CI/CD environments.
 */
public class PlaywrightLocara {
    private static final LocaraEngine ENGINE = new LocaraEngine();
    private static int defaultProbeTimeoutMs = 2500;

    static {
        // Enforce WEB target profile by default for web semantics & WAI-ARIA weighting
        ENGINE.getConfig().setAppTarget(AppTarget.WEB);
    }

    public static LocaraEngine getEngine() {
        return ENGINE;
    }

    public static void setDefaultProbeTimeoutMs(int timeoutMs) {
        defaultProbeTimeoutMs = Math.max(500, timeoutMs);
    }

    public static int getDefaultProbeTimeoutMs() {
        return defaultProbeTimeoutMs;
    }

    /**
     * Clicks an element on a Playwright Page instance.
     * If the original locator/selector fails (broken CSS/XPath/ID), autonomously heals and clicks.
     *
     * @param page             Playwright Page instance (`com.microsoft.playwright.Page`)
     * @param selectorOrXPath  Original selector (e.g. "#submit-btn", "//button[@id='btn']", "button[name='login']")
     * @param targetHint       Human-readable semantic hint (e.g. "Tombol Masuk", "Checkout", "Beli Sekarang")
     * @return true if successfully clicked (original or healed)
     */
    public static boolean click(Object page, String selectorOrXPath, String targetHint) {
        return click(page, selectorOrXPath, targetHint, defaultProbeTimeoutMs);
    }

    /**
     * Clicks an element with configurable probe timeout before healing.
     */
    public static boolean click(Object page, String selectorOrXPath, String targetHint, int probeTimeoutMs) {
        if (page == null) {
            throw new IllegalArgumentException("[PlaywrightLocara] Page instance cannot be null!");
        }

        // 1. Attempt standard execution first
        try {
            Object locator = findLocator(page, selectorOrXPath);
            if (locator != null) {
                // Try clicking with fast probe timeout option if supported, or standard click
                boolean clicked = invokeClick(locator, probeTimeoutMs);
                if (clicked) {
                    return true;
                }
            }
        } catch (Throwable t) {
            System.out.println("[PlaywrightLocara] Standard locator failed: '" + selectorOrXPath + "' -> Initiating autonomous healing...");
        }

        // 2. Self-Healing Execution
        return executeHealing(page, selectorOrXPath, targetHint, "CLICK", null);
    }

    /**
     * Fills/types text into an input field on a Playwright Page with autonomous self-healing.
     *
     * @param page             Playwright Page instance
     * @param selectorOrXPath  Original selector (e.g. "input#username", "//input[@name='user']")
     * @param text             Text to fill
     * @param targetHint       Human-readable semantic hint (e.g. "Username", "Email", "Nomor Rekening")
     * @return true if successfully filled
     */
    public static boolean fill(Object page, String selectorOrXPath, String text, String targetHint) {
        if (page == null) {
            throw new IllegalArgumentException("[PlaywrightLocara] Page instance cannot be null!");
        }

        try {
            Object locator = findLocator(page, selectorOrXPath);
            if (locator != null) {
                boolean filled = invokeFill(locator, text);
                if (filled) {
                    return true;
                }
            }
        } catch (Throwable t) {
            System.out.println("[PlaywrightLocara] Standard locator failed: '" + selectorOrXPath + "' -> Initiating autonomous healing...");
        }

        return executeHealing(page, selectorOrXPath, targetHint, "FILL", text);
    }

    /**
     * Obtains the text content of an element with autonomous self-healing.
     */
    public static String textContent(Object page, String selectorOrXPath, String targetHint) {
        if (page == null) {
            throw new IllegalArgumentException("[PlaywrightLocara] Page instance cannot be null!");
        }

        try {
            Object locator = findLocator(page, selectorOrXPath);
            if (locator != null) {
                Method textMethod = locator.getClass().getMethod("textContent");
                Object result = textMethod.invoke(locator);
                if (result != null) return result.toString();
            }
        } catch (Throwable ignored) {}

        String healedSelector = healSelector(page, selectorOrXPath, targetHint, "GET_TEXT");
        if (healedSelector != null) {
            try {
                Object locator = findLocator(page, healedSelector);
                if (locator != null) {
                    Method textMethod = locator.getClass().getMethod("textContent");
                    Object res = textMethod.invoke(locator);
                    return res != null ? res.toString() : "";
                }
            } catch (Throwable t) {
                System.err.println("[PlaywrightLocara] Failed to get textContent from healed locator: " + t.getMessage());
            }
        }

        return null;
    }

    /**
     * Verifies whether an element is visible on the page with autonomous self-healing.
     */
    public static boolean isVisible(Object page, String selectorOrXPath, String targetHint) {
        if (page == null) return false;

        try {
            Object locator = findLocator(page, selectorOrXPath);
            if (locator != null) {
                Method visMethod = locator.getClass().getMethod("isVisible");
                Object res = visMethod.invoke(locator);
                if (Boolean.TRUE.equals(res)) return true;
            }
        } catch (Throwable ignored) {}

        String healedSelector = healSelector(page, selectorOrXPath, targetHint, "VISIBLE");
        if (healedSelector != null) {
            try {
                Object locator = findLocator(page, healedSelector);
                if (locator != null) {
                    Method visMethod = locator.getClass().getMethod("isVisible");
                    Object res = visMethod.invoke(locator);
                    return Boolean.TRUE.equals(res);
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    /**
     * Returns a Playwright Locator object, automatically healing it if the original selector breaks.
     */
    public static Object locator(Object page, String selectorOrXPath, String targetHint) {
        if (page == null) return null;

        // Verify if original selector matches at least 1 element
        try {
            Object loc = findLocator(page, selectorOrXPath);
            if (loc != null) {
                Method countMethod = loc.getClass().getMethod("count");
                Object countRes = countMethod.invoke(loc);
                if (countRes instanceof Number && ((Number) countRes).intValue() > 0) {
                    return loc;
                }
            }
        } catch (Throwable ignored) {}

        // Autonomous healing
        String healed = healSelector(page, selectorOrXPath, targetHint, "LOCATOR");
        if (healed != null) {
            return findLocator(page, healed);
        }

        // Fallback to original locator if healing finds no candidate
        return findLocator(page, selectorOrXPath);
    }

    /**
     * Executes the autonomous healing cycle:
     * 1. Extracts active HTML DOM via `page.content()`.
     * 2. Feeds DOM into LocaraEngine with WAI-ARIA & Modern Web weight profile.
     * 3. Dispatches action to healed Playwright Locator.
     */
    private static boolean executeHealing(Object page, String originalSelector, String targetHint, String action, String textArg) {
        String htmlContent = extractHtmlContent(page);
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            System.err.println("[PlaywrightLocara] Error: Page HTML DOM is empty or inaccessible!");
            return false;
        }

        String screenName = extractPageTitleOrUrl(page);
        HealContext context = HealContext.builder()
                .targetHint(targetHint)
                .action(action)
                .originalLocator(originalSelector)
                .screenName(screenName)
                .build();

        HealResult result = ENGINE.heal(htmlContent, context);

        if (!result.isSuccess() || result.getCandidate() == null) {
            System.err.println("[PlaywrightLocara] Healing FAILED for hint '" + targetHint + "': " + result.getMessage());
            return false;
        }

        String healedXPath = result.getCandidate().getSuggestedXPath();
        double confidence = result.getCandidate().getConfidenceScore();
        System.out.println(String.format("[PlaywrightLocara] SUCCESS: Healed '%s' -> '%s' (Confidence: %.1f%%)",
                originalSelector, healedXPath, confidence * 100.0));

        // Format selector for Playwright (Playwright accepts raw xpath starting with // or xpath=...)
        String playwrightSelector = healedXPath.startsWith("//") ? "xpath=" + healedXPath : healedXPath;

        try {
            Object healedLocator = findLocator(page, playwrightSelector);
            if (healedLocator == null) {
                // Try raw xpath without prefix
                healedLocator = findLocator(page, healedXPath);
            }

            if (healedLocator != null) {
                if ("FILL".equalsIgnoreCase(action)) {
                    return invokeFill(healedLocator, textArg != null ? textArg : "");
                } else {
                    return invokeClick(healedLocator, 5000);
                }
            }
        } catch (Throwable t) {
            System.err.println("[PlaywrightLocara] Error executing action on healed locator: " + t.getMessage());
        }

        return false;
    }

    /**
     * Resolves the healed selector string without executing an immediate click or fill.
     */
    public static String healSelector(Object page, String originalSelector, String targetHint, String action) {
        String htmlContent = extractHtmlContent(page);
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return null;
        }

        String screenName = extractPageTitleOrUrl(page);
        HealContext context = HealContext.builder()
                .targetHint(targetHint)
                .action(action)
                .originalLocator(originalSelector)
                .screenName(screenName)
                .build();

        HealResult result = ENGINE.heal(htmlContent, context);
        if (result.isSuccess() && result.getCandidate() != null) {
            String xpath = result.getCandidate().getSuggestedXPath();
            return xpath.startsWith("//") ? "xpath=" + xpath : xpath;
        }
        return null;
    }

    // --- Reflection Helpers for Playwright Page & Locator ---

    private static Object findLocator(Object page, String selector) {
        try {
            Method locatorMethod = page.getClass().getMethod("locator", String.class);
            return locatorMethod.invoke(page, selector);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean invokeClick(Object locator, int timeoutMs) {
        try {
            // Try click with ClickOptions if class exists
            Method[] methods = locator.getClass().getMethods();
            for (Method m : methods) {
                if ("click".equals(m.getName()) && m.getParameterCount() == 0) {
                    m.invoke(locator);
                    return true;
                }
            }
        } catch (Throwable t) {
            // Click failed or timed out
        }
        return false;
    }

    private static boolean invokeFill(Object locator, String text) {
        try {
            Method fillMethod = locator.getClass().getMethod("fill", String.class);
            fillMethod.invoke(locator, text);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String extractHtmlContent(Object page) {
        try {
            Method contentMethod = page.getClass().getMethod("content");
            Object res = contentMethod.invoke(page);
            return res != null ? res.toString() : "";
        } catch (Throwable t) {
            return null;
        }
    }

    private static String extractPageTitleOrUrl(Object page) {
        try {
            Method titleMethod = page.getClass().getMethod("title");
            Object title = titleMethod.invoke(page);
            if (title != null && !title.toString().trim().isEmpty()) {
                return title.toString().trim();
            }
            Method urlMethod = page.getClass().getMethod("url");
            Object url = urlMethod.invoke(page);
            if (url != null) return url.toString();
        } catch (Throwable ignored) {}
        return "PlaywrightWebPage";
    }
}
