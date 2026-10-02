package com.qureon.locara.katalon;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;

import com.qureon.locara.core.model.HealConfig;

import java.io.File;
import java.lang.reflect.Method;

/**
 * Public User API for Katalon Studio Test Cases and Screen Flows.
 * Drop-in smart wrapper for mobile actions with autonomous self-healing and auto-spy.
 */
public class Locara {
    private static final LocaraEngine ENGINE = createDefaultEngine();
    private static final KeyboardGuard KEYBOARD_GUARD = new KeyboardGuard();
    private static final ScrollRecoveryEngine SCROLL_ENGINE = new ScrollRecoveryEngine(2);

    private static LocaraEngine createDefaultEngine() {
        String userDir = System.getProperty("user.dir");
        File[] candidates = new File[] {
                new File(userDir, "locara.properties"),
                new File(userDir, "qureon.properties"),
                new File(userDir, "Include/config/locara.properties"),
                new File(userDir, "Include/config/qureon.properties"),
                new File(userDir, "zero-healing.properties"),
                new File(userDir, "zerohealing.properties"),
                new File(userDir, "Include/config/zero-healing.properties"),
                new File(userDir, "Include/config/zerohealing.properties")
        };
        File targetProp = null;
        for (File f : candidates) {
            if (f.exists()) {
                targetProp = f;
                break;
            }
        }
        HealConfig config = targetProp != null ? HealConfig.loadFromProperties(targetProp) : new HealConfig();
        if (targetProp != null) {
            System.out.println("[Locara by Qureon] Loaded configuration from: " + targetProp.getAbsolutePath());
        }
        return new LocaraEngine(config);
    }

    public static void setAutoGenerateRs(boolean enable) {
        ENGINE.getConfig().setAutoGenerateRs(enable);
        System.out.println("[Locara by Qureon] Auto-generate .rs files on disk: " + (enable ? "ENABLED" : "DISABLED"));
    }

    public static void setAllowOverwriteRs(boolean enable) {
        ENGINE.getConfig().setAllowOverwriteExistingRs(enable);
        System.out.println("[Locara by Qureon] Overwrite existing .rs files directly: " + (enable ? "ENABLED" : "DISABLED (quarantine as .healed.rs)"));
    }

    public static LocaraEngine getEngine() {
        return ENGINE;
    }

    public static KeyboardGuard getKeyboardGuard() {
        return KEYBOARD_GUARD;
    }

    public static ScrollRecoveryEngine getScrollEngine() {
        return SCROLL_ENGINE;
    }

    /**
     * Taps a target TestObject. If missing or changed, auto-heals and continues.
     */
    public static boolean tap(Object testObject, String hint) {
        return tap(testObject, 10, hint);
    }

    public static boolean tap(Object testObject, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) {
            try {
                return invokeKatalonTap(testObject, timeoutSeconds);
            } catch (Throwable t) {
                return false;
            }
        }
        try {
            // Attempt standard Katalon Mobile tap first
            boolean success = invokeKatalonTap(testObject, timeoutSeconds);
            if (success) return true;
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] Intercepted tap failure on: " + hint + " (" + t.getMessage() + ")");
        }

        // Trigger Self-Healing
        return executeHealing(testObject, hint, "TAP", "", timeoutSeconds, null);
    }

    /**
     * Directly triggers self-healing for tap without first re-invoking Mobile.tap.
     * Used by hooks and listeners to prevent recursion loops.
     */
    public static boolean healAndTap(Object testObject, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        return executeHealing(testObject, hint, "TAP", "", timeoutSeconds, null);
    }

    /**
     * Taps an element by its semantic hint without requiring a prior spy in Object Repository!
     */
    public static boolean tapByHint(String hint, String targetRsPath) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        return executeHealing(null, hint, "TAP", "", 10, targetRsPath);
    }

    /**
     * Enters text into an input field with autonomous healing.
     */
    public static boolean setText(Object testObject, String text, String hint) {
        if (!ENGINE.getConfig().isEnabled()) {
            try {
                return invokeKatalonSetText(testObject, text, 10);
            } catch (Throwable t) {
                return false;
            }
        }
        try {
            boolean success = invokeKatalonSetText(testObject, text, 10);
            if (success) return true;
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] Intercepted setText failure on: " + hint + " (" + t.getMessage() + ")");
        }

        return executeHealing(testObject, hint, "SET_TEXT", text, 10, null);
    }

    /**
     * Directly triggers self-healing for setText without first re-invoking Mobile.setText.
     * Used by hooks and listeners to prevent recursion loops.
     */
    public static boolean healAndSetText(Object testObject, String text, String hint) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        return executeHealing(testObject, hint, "SET_TEXT", text, 10, null);
    }

    /**
     * Reads text from an element with autonomous self-healing.
     */
    public static String getText(Object testObject, String hint) {
        return getText(testObject, 10, hint);
    }

    public static String getText(Object testObject, int timeoutSeconds, String hint) {
        try {
            Class<?> mobileKeywords = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
            Class<?> toClass = Class.forName("com.kms.katalon.core.testobject.TestObject");
            Method getTextMethod = mobileKeywords.getMethod("getText", toClass, int.class);
            Object result = getTextMethod.invoke(null, testObject, timeoutSeconds);
            if (result != null) return result.toString();
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] Intercepted getText failure on: " + hint + " (" + t.getMessage() + ")");
        }

        if (!ENGINE.getConfig().isEnabled()) return "";

        return healAndGetText(testObject, timeoutSeconds, hint);
    }

    /**
     * Directly triggers self-healing for getText without first re-invoking Mobile.getText.
     */
    public static String healAndGetText(Object testObject, int timeoutSeconds, String hint) {
        Object driver = KatalonDriverBridge.getMobileDriver();
        if (driver == null) {
            throw new RuntimeException("[Locara by Qureon] No active Appium/MobileDriver found in Katalon context!");
        }

        String pageSource = KatalonDriverBridge.getPageSource(driver);
        if (pageSource == null || pageSource.isEmpty()) {
            throw new RuntimeException("[Locara by Qureon] Failed to capture getPageSource() from active driver!");
        }

        String rsPath = extractObjectId(testObject);
        String rawHint = (hint != null && !hint.isEmpty()) ? hint : rsPath;
        String effectiveHint = com.qureon.locara.core.matcher.HeuristicMatcher.cleanSemanticHint(rawHint);
        if (effectiveHint.isEmpty()) effectiveHint = rawHint;

        String expectedTag = extractExpectedTag(rsPath);

        HealContext context = HealContext.builder()
                .targetHint(effectiveHint)
                .action("GET_TEXT")
                .value("")
                .targetRsPath(rsPath)
                .screenName(deriveScreenName(rsPath))
                .originalLocator(extractLocator(testObject))
                .expectedTag(expectedTag)
                .build();

        HealResult result = ENGINE.heal(pageSource, context);

        if (!result.isSuccess() || result.getCandidate() == null) {
            throw new RuntimeException("[Locara by Qureon Failed] " + result.getMessage());
        }

        String healedXPath = result.getCandidate().getSuggestedXPath();
        System.out.println("[Locara by Qureon] 🌟 HEALED: " + hint + " -> XPath: " + healedXPath + " (Score: " +
                String.format(java.util.Locale.US, "%.1f%%", result.getCandidate().getConfidenceScore() * 100.0) + ")");

        if (result.getGeneratedRsFile() != null) {
            System.out.println("[Locara by Qureon] 📝 Object Repository (.rs) written to: " + result.getGeneratedRsFile().getAbsolutePath());
        }

        if (testObject != null) {
            KatalonDriverBridge.patchTestObject(testObject, result.getCandidate().getNode(), healedXPath);
        }

        return executeDirectGetText(driver, healedXPath);
    }

    /**
     * Clears text in an input field with autonomous healing.
     */
    public static boolean clearText(Object testObject, String hint) {
        return clearText(testObject, 10, hint);
    }

    public static boolean clearText(Object testObject, int timeoutSeconds, String hint) {
        try {
            Class<?> mobileKeywords = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
            Class<?> toClass = Class.forName("com.kms.katalon.core.testobject.TestObject");
            Method clearMethod = mobileKeywords.getMethod("clearText", toClass, int.class);
            clearMethod.invoke(null, testObject, timeoutSeconds);
            return true;
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] Intercepted clearText failure on: " + hint + " (" + t.getMessage() + ")");
        }

        return healAndClearText(testObject, timeoutSeconds, hint);
    }

    public static boolean healAndClearText(Object testObject, int timeoutSeconds, String hint) {
        return executeHealing(testObject, hint, "CLEAR_TEXT", "", timeoutSeconds, null);
    }

    private static boolean executeHealing(Object testObject,
                                          String hint,
                                          String action,
                                          String value,
                                          int timeoutSeconds,
                                          String explicitRsPath) {
        Object driver = KatalonDriverBridge.getMobileDriver();
        if (driver == null) {
            throw new RuntimeException("[Locara by Qureon] No active Appium/MobileDriver found in Katalon context!");
        }

        String pageSource = KatalonDriverBridge.getPageSource(driver);
        if (pageSource == null || pageSource.isEmpty()) {
            throw new RuntimeException("[Locara by Qureon] Failed to capture getPageSource() from active driver!");
        }

        String rsPath = explicitRsPath != null ? explicitRsPath : extractObjectId(testObject);
        String rawHint = (hint != null && !hint.isEmpty()) ? hint : rsPath;
        String effectiveHint = com.qureon.locara.core.matcher.HeuristicMatcher.cleanSemanticHint(rawHint);
        if (effectiveHint.isEmpty()) effectiveHint = rawHint;

        String expectedTag = extractExpectedTag(rsPath);

        HealContext context = HealContext.builder()
                .targetHint(effectiveHint)
                .action(action)
                .value(value)
                .targetRsPath(rsPath)
                .screenName(deriveScreenName(rsPath))
                .originalLocator(extractLocator(testObject))
                .expectedTag(expectedTag)
                .build();

        HealResult result = ENGINE.heal(pageSource, context);

        // Auto-Scroll Fallback: If not found, attempt scroll recovery on scrollable containers
        if (!result.isSuccess()) {
            try {
                java.util.List<com.qureon.locara.core.model.UIElementNode> nodes = new com.qureon.locara.core.parser.PageSourceParser().parse(pageSource);
                if (SCROLL_ENGINE.hasScrollableContainer(nodes)) {
                    for (int attempt = 1; attempt <= SCROLL_ENGINE.getMaxScrollAttempts(); attempt++) {
                        System.out.println("[Locara by Qureon] 📜 Target '" + hint + "' not found in active viewport. Attempting auto-scroll recovery #" + attempt + "...");
                        boolean scrolled = SCROLL_ENGINE.performScrollDown(driver);
                        if (!scrolled) break;

                        pageSource = KatalonDriverBridge.getPageSource(driver);
                        if (pageSource != null) {
                            result = ENGINE.heal(pageSource, context);
                            if (result.isSuccess()) {
                                System.out.println("[Locara by Qureon] 🎯 Target '" + hint + "' successfully discovered after scroll recovery!");
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!result.isSuccess() || result.getCandidate() == null) {
            throw new RuntimeException("[Locara by Qureon Failed] " + result.getMessage());
        }

        String healedXPath = result.getCandidate().getSuggestedXPath();
        System.out.println("[Locara by Qureon] 🌟 HEALED: " + hint + " -> XPath: " + healedXPath + " (Score: " +
                String.format(java.util.Locale.US, "%.1f%%", result.getCandidate().getConfidenceScore() * 100.0) + ")");

        if (result.getGeneratedRsFile() != null) {
            System.out.println("[Locara by Qureon] 📝 Object Repository (.rs) written to: " + result.getGeneratedRsFile().getAbsolutePath());
        }

        // The Golden Triad: Keyboard Guard (Auto-dismiss if element is in keyboard zone)
        KEYBOARD_GUARD.guardElement(driver, result.getCandidate().getNode());

        // Patch in-memory TestObject
        if (testObject != null) {
            KatalonDriverBridge.patchTestObject(testObject, result.getCandidate().getNode(), healedXPath);
        } else {
            testObject = KatalonDriverBridge.createDynamicTestObject(hint, result.getCandidate().getNode(), healedXPath);
        }

        // Execute action directly on driver using healed locator
        return executeDirectDriverAction(driver, healedXPath, action, value);
    }

    private static boolean executeDirectDriverAction(Object driver, String xpath, String action, String value) {
        try {
            Class<?> byClass = Class.forName("org.openqa.selenium.By");
            Method xpathMethod = byClass.getMethod("xpath", String.class);
            Object by = xpathMethod.invoke(null, xpath);

            Method findElementMethod = driver.getClass().getMethod("findElement", byClass);
            Object element = findElementMethod.invoke(driver, by);

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
            } else if ("CLEAR_TEXT".equalsIgnoreCase(action)) {
                Method clearMethod = element.getClass().getMethod("clear");
                clearMethod.invoke(element);
                return true;
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException("[Locara by Qureon] Failed to execute retry action on healed element: " + e.getMessage(), e);
        }
    }

    private static String executeDirectGetText(Object driver, String xpath) {
        try {
            Class<?> byClass = Class.forName("org.openqa.selenium.By");
            Method xpathMethod = byClass.getMethod("xpath", String.class);
            Object by = xpathMethod.invoke(null, xpath);

            Method findElementMethod = driver.getClass().getMethod("findElement", byClass);
            Object element = findElementMethod.invoke(driver, by);

            Method getTextMethod = element.getClass().getMethod("getText");
            Object textObj = getTextMethod.invoke(element);
            String text = textObj != null ? textObj.toString() : "";
            if (!text.isEmpty()) {
                return text;
            }

            Method getAttributeMethod = element.getClass().getMethod("getAttribute", String.class);
            Object attrTextObj = getAttributeMethod.invoke(element, "text");
            String attrText = attrTextObj != null ? attrTextObj.toString() : "";
            if (!attrText.isEmpty()) {
                return attrText;
            }

            Object descTextObj = getAttributeMethod.invoke(element, "content-desc");
            String descText = descTextObj != null ? descTextObj.toString() : "";
            if (!descText.isEmpty()) {
                return descText;
            }

            return text;
        } catch (Exception e) {
            throw new RuntimeException("[Locara by Qureon] Failed to read text from healed element: " + e.getMessage(), e);
        }
    }

    private static boolean invokeKatalonTap(Object testObject, int timeout) throws Exception {
        Class<?> mobileKeywords = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
        Class<?> toClass = Class.forName("com.kms.katalon.core.testobject.TestObject");
        Method tapMethod = mobileKeywords.getMethod("tap", toClass, int.class);
        tapMethod.invoke(null, testObject, timeout);
        return true;
    }

    private static boolean invokeKatalonSetText(Object testObject, String text, int timeout) throws Exception {
        Class<?> mobileKeywords = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
        Class<?> toClass = Class.forName("com.kms.katalon.core.testobject.TestObject");
        Method setTextMethod = mobileKeywords.getMethod("setText", toClass, String.class, int.class);
        setTextMethod.invoke(null, testObject, text, timeout);
        return true;
    }

    private static String extractObjectId(Object testObject) {
        if (testObject == null) return "";
        try {
            Method getId = testObject.getClass().getMethod("getObjectId");
            Object id = getId.invoke(testObject);
            return id != null ? id.toString() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String extractLocator(Object testObject) {
        if (testObject == null) return "";
        try {
            Method getXpath = testObject.getClass().getMethod("findPropertyValue", String.class, boolean.class);
            Object val = getXpath.invoke(testObject, "xpath", false);
            return val != null ? val.toString() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String extractExpectedTag(String rsPath) {
        if (rsPath == null || rsPath.isEmpty()) return null;
        String name = rsPath.replace("\\", "/");
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:^[0-9]+\\.\\s*)?(ViewGroup|TextView|Button|EditText|ImageView|ImageButton|CheckBox|RadioButton|View)\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(name);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String deriveScreenName(String rsPath) {
        if (rsPath == null || rsPath.isEmpty()) return "UnknownScreen";
        String normalized = rsPath.replace("\\", "/");
        if (normalized.contains("Object Repository/")) {
            normalized = normalized.substring(normalized.indexOf("Object Repository/") + 18);
        }
        if (normalized.contains("/")) {
            return normalized.substring(0, normalized.indexOf("/"));
        }
        return "DefaultScreen";
    }
}
