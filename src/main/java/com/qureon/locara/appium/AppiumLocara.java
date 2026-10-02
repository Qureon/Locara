package com.qureon.locara.appium;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import com.qureon.locara.katalon.KeyboardGuard;
import com.qureon.locara.katalon.ScrollRecoveryEngine;

import java.lang.reflect.Method;

/**
 * First-Class Standalone Appium Self-Healing & Auto-Recovery API.
 * Compatible with raw AppiumDriver, AndroidDriver, IOSDriver, and WebDriver across
 * Java, Kotlin, TestNG, JUnit, and Cucumber test suites without requiring Katalon Studio.
 */
public class AppiumLocara {
    private static final LocaraEngine ENGINE = new LocaraEngine();
    private static final KeyboardGuard KEYBOARD_GUARD = new KeyboardGuard();
    private static final ScrollRecoveryEngine SCROLL_ENGINE = new ScrollRecoveryEngine(2);

    public static LocaraEngine getEngine() {
        return ENGINE;
    }

    /**
     * Taps an element on raw Appium driver. If original locator fails, autonomously heals and clicks.
     *
     * @param driver         Raw AppiumDriver / AndroidDriver / IOSDriver instance
     * @param locatorOrXPath Original locator (e.g., "//android.widget.Button[@id='old_btn']" or "btn_login")
     * @param targetHint     Human-readable semantic hint (e.g., "Lanjut Transfer", "Masuk", "Konfirmasi")
     * @return true if successfully tapped (original or healed)
     */
    public static boolean tap(Object driver, String locatorOrXPath, String targetHint) {
        if (driver == null) {
            throw new IllegalArgumentException("[AppiumLocara] Driver instance cannot be null!");
        }

        // 1. Attempt standard execution first
        try {
            Object element = findElementDirectly(driver, locatorOrXPath);
            if (element != null) {
                Method clickMethod = element.getClass().getMethod("click");
                clickMethod.invoke(element);
                return true;
            }
        } catch (Throwable t) {
            System.out.println("[AppiumLocara] Standard locator failed: " + locatorOrXPath + " -> Initiating autonomous healing...");
        }

        // 2. Self-Healing Execution
        return executeHealing(driver, locatorOrXPath, targetHint, "TAP", "");
    }

    /**
     * Types text into an input field on raw Appium driver with autonomous self-healing.
     */
    public static boolean setText(Object driver, String locatorOrXPath, String text, String targetHint) {
        if (driver == null) {
            throw new IllegalArgumentException("[AppiumLocara] Driver instance cannot be null!");
        }

        try {
            Object element = findElementDirectly(driver, locatorOrXPath);
            if (element != null) {
                Method clearMethod = element.getClass().getMethod("clear");
                clearMethod.invoke(element);
                Method sendKeysMethod = element.getClass().getMethod("sendKeys", CharSequence[].class);
                sendKeysMethod.invoke(element, (Object) new CharSequence[]{text});
                return true;
            }
        } catch (Throwable t) {
            System.out.println("[AppiumLocara] Standard setText failed: " + locatorOrXPath + " -> Initiating autonomous healing...");
        }

        return executeHealing(driver, locatorOrXPath, targetHint, "SET_TEXT", text);
    }

    /**
     * Finds a WebElement by healing it in real-time if the original locator is broken.
     */
    public static Object findElement(Object driver, String locatorOrXPath, String targetHint) {
        try {
            Object el = findElementDirectly(driver, locatorOrXPath);
            if (el != null) return el;
        } catch (Throwable ignored) {}

        String pageSource = extractPageSource(driver);
        HealContext context = HealContext.builder()
                .targetHint(targetHint)
                .action("FIND")
                .originalLocator(locatorOrXPath)
                .screenName("AppiumScreen")
                .build();

        HealResult result = ENGINE.heal(pageSource, context);

        // Auto-Scroll recovery if not in current viewport
        if (!result.isSuccess()) {
            try {
                java.util.List<com.qureon.locara.core.model.UIElementNode> nodes = new com.qureon.locara.core.parser.PageSourceParser().parse(pageSource);
                if (SCROLL_ENGINE.hasScrollableContainer(nodes)) {
                    for (int attempt = 1; attempt <= SCROLL_ENGINE.getMaxScrollAttempts(); attempt++) {
                        System.out.println("[AppiumLocara] Scrolling down to find '" + targetHint + "' (attempt #" + attempt + ")...");
                        boolean scrolled = SCROLL_ENGINE.performScrollDown(driver);
                        if (!scrolled) break;

                        pageSource = extractPageSource(driver);
                        if (pageSource != null) {
                            result = ENGINE.heal(pageSource, context);
                            if (result.isSuccess()) break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!result.isSuccess() || result.getCandidate() == null) {
            throw new RuntimeException("[AppiumLocara Failed] " + result.getMessage());
        }

        String healedXPath = result.getCandidate().getSuggestedXPath();
        return findElementDirectly(driver, healedXPath);
    }

    private static boolean executeHealing(Object driver, String locatorOrXPath, String targetHint, String action, String value) {
        String pageSource = extractPageSource(driver);
        if (pageSource == null || pageSource.isEmpty()) {
            throw new RuntimeException("[AppiumLocara] Unable to capture page source from Appium driver!");
        }

        HealContext context = HealContext.builder()
                .targetHint(targetHint)
                .action(action)
                .value(value)
                .originalLocator(locatorOrXPath)
                .screenName("AppiumScreen")
                .build();

        HealResult result = ENGINE.heal(pageSource, context);

        // Auto-Scroll recovery if not in current viewport
        if (!result.isSuccess()) {
            try {
                java.util.List<com.qureon.locara.core.model.UIElementNode> nodes = new com.qureon.locara.core.parser.PageSourceParser().parse(pageSource);
                if (SCROLL_ENGINE.hasScrollableContainer(nodes)) {
                    for (int attempt = 1; attempt <= SCROLL_ENGINE.getMaxScrollAttempts(); attempt++) {
                        System.out.println("[AppiumLocara] Scrolling down to find '" + targetHint + "' (attempt #" + attempt + ")...");
                        boolean scrolled = SCROLL_ENGINE.performScrollDown(driver);
                        if (!scrolled) break;

                        pageSource = extractPageSource(driver);
                        if (pageSource != null) {
                            result = ENGINE.heal(pageSource, context);
                            if (result.isSuccess()) break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!result.isSuccess() || result.getCandidate() == null) {
            throw new RuntimeException("[AppiumLocara Failed] " + result.getMessage());
        }

        String healedXPath = result.getCandidate().getSuggestedXPath();
        System.out.printf(java.util.Locale.US, "[AppiumLocara] SUCCESS! Healed '%s' -> %s (Confidence: %.1f%%)%n",
                targetHint, healedXPath, result.getCandidate().getConfidenceScore() * 100.0);

        // Auto-dismiss keyboard if obscuring element
        KEYBOARD_GUARD.guardElement(driver, result.getCandidate().getNode());

        // Execute action directly on driver
        return performDriverAction(driver, healedXPath, action, value);
    }

    private static Object findElementDirectly(Object driver, String locatorOrXPath) {
        try {
            Class<?> byClass = Class.forName("org.openqa.selenium.By");
            Object by;
            if (locatorOrXPath.startsWith("//") || locatorOrXPath.startsWith("xpath=") || locatorOrXPath.startsWith("(")) {
                String cleanXpath = locatorOrXPath.replace("xpath=", "");
                Method xpathMethod = byClass.getMethod("xpath", String.class);
                by = xpathMethod.invoke(null, cleanXpath);
            } else {
                Method idMethod = byClass.getMethod("id", String.class);
                by = idMethod.invoke(null, locatorOrXPath);
            }

            Method findElementMethod = driver.getClass().getMethod("findElement", byClass);
            return findElementMethod.invoke(driver, by);
        } catch (ClassNotFoundException e) {
            // Fallback for mock or generic drivers without Selenium API on test classpath
            try {
                Method m = driver.getClass().getMethod("findElementByXPath", String.class);
                return m.invoke(driver, locatorOrXPath);
            } catch (Exception ignored) {
                try {
                    Method m = driver.getClass().getMethod("findElement", Object.class);
                    return m.invoke(driver, locatorOrXPath);
                } catch (Exception ignored2) {
                    return null;
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean performDriverAction(Object driver, String xpath, String action, String value) {
        try {
            Object element = null;
            try {
                Class<?> byClass = Class.forName("org.openqa.selenium.By");
                Method xpathMethod = byClass.getMethod("xpath", String.class);
                Object by = xpathMethod.invoke(null, xpath);

                Method findElementMethod = driver.getClass().getMethod("findElement", byClass);
                element = findElementMethod.invoke(driver, by);
            } catch (ClassNotFoundException e) {
                // Fallback for mock drivers
                try {
                    Method m = driver.getClass().getMethod("findElementByXPath", String.class);
                    element = m.invoke(driver, xpath);
                } catch (Exception ignored) {
                    Method m = driver.getClass().getMethod("findElement", Object.class);
                    element = m.invoke(driver, xpath);
                }
            }

            if (element == null) {
                throw new RuntimeException("Element not found by driver for XPath: " + xpath);
            }

            if ("TAP".equalsIgnoreCase(action) || "CLICK".equalsIgnoreCase(action)) {
                Method clickMethod = element.getClass().getMethod("click");
                clickMethod.invoke(element);
                return true;
            } else if ("SET_TEXT".equalsIgnoreCase(action)) {
                Method clearMethod = element.getClass().getMethod("clear");
                clearMethod.invoke(element);
                Method sendKeysMethod = element.getClass().getMethod("sendKeys", CharSequence[].class);
                sendKeysMethod.invoke(element, (Object) new CharSequence[]{value});
                return true;
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException("[AppiumLocara] Failed to execute action on healed element: " + e.getMessage(), e);
        }
    }

    private static String extractPageSource(Object driver) {
        if (driver == null) return null;
        try {
            Method getPageSourceMethod = driver.getClass().getMethod("getPageSource");
            Object res = getPageSourceMethod.invoke(driver);
            return res != null ? res.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
