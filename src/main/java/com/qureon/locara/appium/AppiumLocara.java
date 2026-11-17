package com.qureon.locara.appium;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.driver.AppiumDriverActionBridge;
import com.qureon.locara.core.logging.LocaraLogger;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.katalon.KeyboardGuard;
import com.qureon.locara.katalon.ManualScrollGuard;
import com.qureon.locara.katalon.ScrollRecoveryEngine;

import java.util.List;
import java.util.Locale;

/**
 * First-Class Standalone Appium Self-Healing & Auto-Recovery API.
 * Compatible with raw AppiumDriver, AndroidDriver, IOSDriver, and WebDriver across
 * Java, Kotlin, TestNG, JUnit, and Cucumber test suites without requiring Katalon Studio.
 */
public class AppiumLocara {

    private static final String LOG_PREFIX = "[AppiumLocara]";
    private static final LocaraEngine ENGINE = new LocaraEngine();
    private static final KeyboardGuard KEYBOARD_GUARD = new KeyboardGuard();
    private static volatile ScrollRecoveryEngine scrollEngine =
            new ScrollRecoveryEngine(ENGINE.getConfig().getMaxScrollAttempts());

    public static LocaraEngine getEngine() {
        return ENGINE;
    }

    public static ScrollRecoveryEngine getScrollEngine() {
        int configured = ENGINE.getConfig().getMaxScrollAttempts();
        if (scrollEngine.getMaxScrollAttempts() != configured) {
            scrollEngine = new ScrollRecoveryEngine(configured);
        }
        return scrollEngine;
    }

    static void setScrollEngineForTests(ScrollRecoveryEngine customScrollEngine) {
        scrollEngine = customScrollEngine != null
                ? customScrollEngine
                : new ScrollRecoveryEngine(ENGINE.getConfig().getMaxScrollAttempts());
    }

    public static void setScrollRecoveryEnabled(boolean enable) {
        ENGINE.getConfig().setScrollRecoveryEnabled(enable);
        LocaraLogger.info(LOG_PREFIX, "Auto-scroll recovery: " + (enable ? "ENABLED" : "DISABLED"));
    }

    public static boolean isScrollRecoveryEnabled() {
        return ENGINE.getConfig().isScrollRecoveryEnabled();
    }

    public static void notifyManualScroll() {
        ManualScrollGuard.recordManualScroll();
    }

    public static void clearManualScrollState() {
        ManualScrollGuard.clear();
    }

    /**
     * Taps an element on raw Appium driver. If original locator fails, autonomously heals and clicks.
     */
    public static boolean tap(Object driver, String locatorOrXPath, String targetHint) {
        requireDriver(driver);

        try (ManualScrollGuard.ActionWindow ignored = ManualScrollGuard.openActionWindow(ENGINE.getConfig())) {
            try {
                Object element = AppiumDriverActionBridge.findElementDirectly(driver, locatorOrXPath);
                if (element != null && AppiumDriverActionBridge.executeElementAction(element, "TAP", "")) {
                    ManualScrollGuard.markActionTapSucceeded();
                    return true;
                }
            } catch (Throwable t) {
                LocaraLogger.info(LOG_PREFIX, "Standard locator failed: " + locatorOrXPath + " -> Initiating autonomous healing...");
            }

            if (!ENGINE.getConfig().isEnabled()) return false;
            boolean healed = executeHealing(driver, locatorOrXPath, targetHint, "TAP", "");
            if (healed) {
                ManualScrollGuard.markActionTapSucceeded();
            }
            return healed;
        }
    }

    /**
     * Types text into an input field on raw Appium driver with autonomous self-healing.
     */
    public static boolean setText(Object driver, String locatorOrXPath, String text, String targetHint) {
        requireDriver(driver);

        try (ManualScrollGuard.ActionWindow ignored = ManualScrollGuard.openActionWindow(ENGINE.getConfig())) {
            try {
                Object element = AppiumDriverActionBridge.findElementDirectly(driver, locatorOrXPath);
                if (element != null && AppiumDriverActionBridge.executeElementAction(element, "SET_TEXT", text)) {
                    return true;
                }
            } catch (Throwable t) {
                LocaraLogger.info(LOG_PREFIX, "Standard setText failed: " + locatorOrXPath + " -> Initiating autonomous healing...");
            }

            if (!ENGINE.getConfig().isEnabled()) return false;
            return executeHealing(driver, locatorOrXPath, targetHint, "SET_TEXT", text);
        }
    }

    /**
     * Finds a WebElement by healing it in real-time if the original locator is broken.
     */
    public static Object findElement(Object driver, String locatorOrXPath, String targetHint) {
        requireDriver(driver);

        try (ManualScrollGuard.ActionWindow ignored = ManualScrollGuard.openActionWindow(ENGINE.getConfig())) {
            Object direct = AppiumDriverActionBridge.findElementDirectly(driver, locatorOrXPath);
            if (direct != null) {
                return direct;
            }

            if (!ENGINE.getConfig().isEnabled()) {
                throw new RuntimeException("[AppiumLocara] Element not found and self-healing is disabled: " + locatorOrXPath);
            }

            HealResult result = healWithScrollRecovery(driver, locatorOrXPath, targetHint, "FIND", "");
            return AppiumDriverActionBridge.findElementDirectly(driver, result.getCandidate().getSuggestedXPath());
        }
    }

    private static boolean executeHealing(Object driver, String locatorOrXPath, String targetHint, String action, String value) {
        HealResult result = healWithScrollRecovery(driver, locatorOrXPath, targetHint, action, value);

        String healedXPath = result.getCandidate().getSuggestedXPath();
        LocaraLogger.info(LOG_PREFIX, String.format(Locale.US, "SUCCESS! Healed '%s' -> %s (Confidence: %.1f%%)",
                targetHint, healedXPath, result.getCandidate().getConfidenceScore() * 100.0));

        KEYBOARD_GUARD.guardElement(driver, result.getCandidate().getNode());

        try {
            Object element = AppiumDriverActionBridge.findElementDirectly(driver, healedXPath);
            if (element == null) {
                throw new RuntimeException("Element not found by driver for XPath: " + healedXPath);
            }
            return AppiumDriverActionBridge.executeElementAction(element, action, value);
        } catch (Exception e) {
            throw new RuntimeException("[AppiumLocara] Failed to execute action on healed element: " + e.getMessage(), e);
        }
    }

    private static HealResult healWithScrollRecovery(Object driver, String locatorOrXPath, String targetHint,
                                                     String action, String value) {
        String pageSource = AppiumDriverActionBridge.extractPageSource(driver);
        if (pageSource == null || pageSource.isEmpty()) {
            throw new RuntimeException("[AppiumLocara] Unable to capture page source from Appium driver!");
        }

        HealContext context = HealContext.builder()
                .targetHint(targetHint)
                .action(action)
                .value(value != null ? value : "")
                .originalLocator(locatorOrXPath)
                .screenName("AppiumScreen")
                .build();

        List<UIElementNode> nodes;
        HealResult result;
        try {
            nodes = ENGINE.parsePageSource(pageSource);
            result = ENGINE.heal(nodes, pageSource, context);
        } catch (Exception e) {
            throw new RuntimeException("[AppiumLocara Failed] Failed to parse page source: " + e.getMessage(), e);
        }

        if (!result.isSuccess()) {
            result = getScrollEngine().recoverWithScroll(
                    ENGINE.getConfig(), ENGINE, context, nodes, pageSource, result,
                    driver, () -> AppiumDriverActionBridge.extractPageSource(driver), LOG_PREFIX
            );
        }

        if (!result.isSuccess() || result.getCandidate() == null) {
            throw new RuntimeException("[AppiumLocara Failed] " + result.getMessage());
        }
        return result;
    }

    private static void requireDriver(Object driver) {
        if (driver == null) {
            throw new IllegalArgumentException("[AppiumLocara] Driver instance cannot be null!");
        }
    }
}
