package com.qureon.locara.katalon;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;

import com.qureon.locara.core.model.HealConfig;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public User API for Katalon Studio Test Cases and Screen Flows.
 * Drop-in smart wrapper for mobile actions with autonomous self-healing and auto-spy.
 */
public class Locara {
    private static final LocaraEngine ENGINE = createDefaultEngine();
    private static final KeyboardGuard KEYBOARD_GUARD = new KeyboardGuard();
    private static volatile ScrollRecoveryEngine scrollEngine = new ScrollRecoveryEngine(ENGINE.getConfig().getMaxScrollAttempts());
    private static final com.qureon.locara.core.guard.OverlayAirbag OVERLAY_AIRBAG = new com.qureon.locara.core.guard.OverlayAirbag();
    private static final com.qureon.locara.core.context.ContextualEntityResolver CONTEXT_RESOLVER = new com.qureon.locara.core.context.ContextualEntityResolver();
    private static final com.qureon.locara.katalon.variable.AutoVariableSynthesizer VARIABLE_SYNTHESIZER = new com.qureon.locara.katalon.variable.AutoVariableSynthesizer();
    private static final com.qureon.locara.katalon.variable.ScriptVariableAligner SCRIPT_ALIGNER = new com.qureon.locara.katalon.variable.ScriptVariableAligner();
    private static final com.qureon.locara.core.generator.DynamicXPathGenerator XPATH_GENERATOR = new com.qureon.locara.core.generator.DynamicXPathGenerator();

    private static final Pattern LOCATOR_ATTR_PATTERN = Pattern.compile("@(?:text|content-desc)\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern EXPECTED_TAG_PATTERN = Pattern.compile("^(?:[0-9]+(?:-[0-9]+)?\\.\\s*)?(ViewGroup|TextView|Button|EditText|ImageView|ImageButton|CheckBox|RadioButton|View)\\b", Pattern.CASE_INSENSITIVE);
    private static volatile Class<?> cachedByClass;
    private static volatile Method cachedByXPathMethod;
    private static volatile Class<?> cachedMobileKeywordsClass;
    private static volatile Class<?> cachedTestObjectClass;

    private static LocaraEngine createDefaultEngine() {
        String userDir = System.getProperty("user.dir");
        File[] candidates = new File[] {
                new File(userDir, "locara.properties"),
                new File(userDir, "qureon.properties"),
                new File(userDir, "Include/config/locara.properties"),
                new File(userDir, "Include/config/qureon.properties")
        };
        File targetProp = null;
        for (File f : candidates) {
            if (f.exists()) {
                targetProp = f;
                break;
            }
        }
        HealConfig config = HealConfig.loadFromProperties(targetProp);
        if (targetProp != null) {
            System.out.println("[Locara by Qureon] Loaded configuration from: " + targetProp.getAbsolutePath());
        }
        return new LocaraEngine(config);
    }

    private static volatile com.qureon.locara.core.model.AppTarget defaultTarget = com.qureon.locara.core.model.AppTarget.AUTO;
    private static final ThreadLocal<com.qureon.locara.core.model.AppTarget> threadTarget = new ThreadLocal<>();

    /**
     * Sets the high-level application optimization profile.
     * Options:
     * - AppTarget.UMUM   : Universal app-agnostic mode (Standard balanced weights, 2D spatial alignment)
     * - AppTarget.REVAMP : React Native / Revamp M-Smile mode (Parent container ascension, text/desc priority)
     * - AppTarget.KMP    : Kotlin Multiplatform / Compose mode (TestID morphology, scrambled PIN, dynamic tokens)
     * - AppTarget.AUTO   : Adaptive runtime fingerprinting (Default)
     */
    public static void setAppTarget(com.qureon.locara.core.model.AppTarget target) {
        com.qureon.locara.core.model.AppTarget active = target != null ? target : com.qureon.locara.core.model.AppTarget.AUTO;
        defaultTarget = active;
        threadTarget.remove();
        ENGINE.getConfig().setAppTarget(active);
        System.out.println("[Locara by Qureon] Active Target Profile: " + active.getDescription());
    }

    public static void setThreadAppTarget(com.qureon.locara.core.model.AppTarget target) {
        if (target == null) {
            clearThreadAppTarget();
            return;
        }
        threadTarget.set(target);
        ENGINE.getConfig().setThreadAppTarget(target);
    }

    public static void clearThreadAppTarget() {
        threadTarget.remove();
        ENGINE.getConfig().clearThreadAppTarget();
    }

    public static com.qureon.locara.core.model.AppTarget getAppTarget() {
        com.qureon.locara.core.model.AppTarget target = threadTarget.get();
        if (target != null) return target;
        com.qureon.locara.core.model.AppTarget configTarget = ENGINE.getConfig().getAppTarget();
        return configTarget != null ? configTarget : defaultTarget;
    }

    public static void resetSessionContext() {
        threadTarget.remove();
        ENGINE.resetSessionContext();
    }

    public static void setProfileUmum() { setAppTarget(com.qureon.locara.core.model.AppTarget.UMUM); }
    public static void setProfileRevamp() { setAppTarget(com.qureon.locara.core.model.AppTarget.REVAMP); }
    public static void setProfileKmp() { setAppTarget(com.qureon.locara.core.model.AppTarget.KMP); }
    public static void setProfileAuto() { setAppTarget(com.qureon.locara.core.model.AppTarget.AUTO); }

    public static void setAutoGenerateRs(boolean enable) {
        ENGINE.getConfig().setAutoGenerateRs(enable);
        System.out.println("[Locara by Qureon] Auto-generate .rs files on disk: " + (enable ? "ENABLED" : "DISABLED"));
    }

    public static void setAllowOverwriteRs(boolean enable) {
        ENGINE.getConfig().setAllowOverwriteExistingRs(enable);
        System.out.println("[Locara by Qureon] Overwrite existing .rs files directly: " + (enable ? "ENABLED" : "DISABLED (quarantine as .healed.rs)"));
    }

    public static void setScrollRecoveryEnabled(boolean enable) {
        ENGINE.getConfig().setScrollRecoveryEnabled(enable);
        System.out.println("[Locara by Qureon] Auto-scroll recovery: " + (enable ? "ENABLED" : "DISABLED"));
    }

    public static boolean isScrollRecoveryEnabled() {
        return ENGINE.getConfig().isScrollRecoveryEnabled();
    }

    public static void setMaxScrollAttempts(int maxAttempts) {
        ENGINE.getConfig().setMaxScrollAttempts(maxAttempts);
        scrollEngine = new ScrollRecoveryEngine(ENGINE.getConfig().getMaxScrollAttempts());
    }

    public static LocaraEngine getEngine() {
        return ENGINE;
    }

    public static KeyboardGuard getKeyboardGuard() {
        return KEYBOARD_GUARD;
    }

    public static ScrollRecoveryEngine getScrollEngine() {
        int configured = ENGINE.getConfig().getMaxScrollAttempts();
        if (scrollEngine.getMaxScrollAttempts() != configured) {
            scrollEngine = new ScrollRecoveryEngine(configured);
        }
        return scrollEngine;
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
        return tapByHint(hint, 10, targetRsPath);
    }

    public static boolean tapByHint(String hint, int timeoutSeconds, String targetRsPath) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        return executeHealing(null, hint, "TAP", "", timeoutSeconds, targetRsPath);
    }

    /**
     * Enters text into an input field with autonomous healing.
     */
    public static boolean setText(Object testObject, String text, String hint) {
        return setText(testObject, text, 10, hint);
    }

    public static boolean setText(Object testObject, String text, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) {
            try {
                return invokeKatalonSetText(testObject, text, timeoutSeconds);
            } catch (Throwable t) {
                return false;
            }
        }
        try {
            boolean success = invokeKatalonSetText(testObject, text, timeoutSeconds);
            if (success) return true;
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] Intercepted setText failure on: " + hint + " (" + t.getMessage() + ")");
        }

        return executeHealing(testObject, hint, "SET_TEXT", text, timeoutSeconds, null);
    }

    /**
     * Directly triggers self-healing for setText without first re-invoking Mobile.setText.
     * Used by hooks and listeners to prevent recursion loops.
     */
    public static boolean healAndSetText(Object testObject, String text, String hint) {
        return healAndSetText(testObject, text, 10, hint);
    }

    public static boolean healAndSetText(Object testObject, String text, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        return executeHealing(testObject, hint, "SET_TEXT", text, timeoutSeconds, null);
    }

    /**
     * Reads text from an element with autonomous self-healing.
     */
    public static String getText(Object testObject, String hint) {
        return getText(testObject, 10, hint);
    }

    public static String getText(Object testObject, int timeoutSeconds, String hint) {
        try {
            Method getTextMethod = getMobileKeywordMethod("getText", getTestObjectClass(), int.class);
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

        HealContext context = buildHealContext(testObject, hint, "GET_TEXT", "", extractObjectId(testObject));
        List<com.qureon.locara.core.model.UIElementNode> nodes = safeParsePageSource(pageSource);
        HealResult result = ENGINE.heal(nodes, pageSource, context);

        // Overlay Airbag Fallback: If target not found, check for blocking OS permission or modal overlay
        if (!result.isSuccess()) {
            boolean dismissed = OVERLAY_AIRBAG.dismissBlockingOverlay(driver, nodes);
            if (dismissed) {
                pageSource = KatalonDriverBridge.getPageSource(driver);
                if (pageSource != null) {
                    result = ENGINE.heal(safeParsePageSource(pageSource), pageSource, context);
                }
            }
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

        if (testObject != null) {
            KatalonDriverBridge.patchTestObject(testObject, result.getCandidate().getNode(), healedXPath);
        }

        return executeDirectGetText(driver, healedXPath, timeoutSeconds);
    }

    /**
     * Clears text in an input field with autonomous healing.
     */
    public static boolean clearText(Object testObject, String hint) {
        return clearText(testObject, 10, hint);
    }

    public static boolean clearText(Object testObject, int timeoutSeconds, String hint) {
        try {
            Method clearMethod = getMobileKeywordMethod("clearText", getTestObjectClass(), int.class);
            clearMethod.invoke(null, testObject, timeoutSeconds);
            return true;
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] Intercepted clearText failure on: " + hint + " (" + t.getMessage() + ")");
        }

        if (!ENGINE.getConfig().isEnabled()) return false;
        return healAndClearText(testObject, timeoutSeconds, hint);
    }

    public static boolean healAndClearText(Object testObject, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        return executeHealing(testObject, hint, "CLEAR_TEXT", "", timeoutSeconds, null);
    }

    /**
     * Directly triggers self-healing for waitForElementPresent and verifyElementPresent.
     * Evaluates UI hierarchy via multi-vector heuristics and semantic intent matching.
     * If found, patches the in-memory TestObject and returns true.
     */
    public static boolean healAndVerifyPresent(Object testObject, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        Object driver = KatalonDriverBridge.getMobileDriver();
        String healedXPath = resolvePresentXPathAndPatch(driver, testObject, hint);
        if (healedXPath == null || healedXPath.isEmpty()) {
            return false;
        }
        return verifyElementExists(driver, healedXPath);
    }

    /**
     * Directly triggers self-healing for verifyElementVisible.
     */
    public static boolean healAndVerifyVisible(Object testObject, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        Object driver = KatalonDriverBridge.getMobileDriver();
        String healedXPath = resolvePresentXPathAndPatch(driver, testObject, hint);
        if (healedXPath == null || healedXPath.isEmpty()) {
            return false;
        }
        return verifyElementDisplayed(driver, healedXPath);
    }

    private static String resolvePresentXPathAndPatch(Object driver, Object testObject, String hint) {
        if (driver == null) {
            return null;
        }
        try {
            String pageSource = KatalonDriverBridge.getPageSource(driver);
            if (pageSource == null || pageSource.isEmpty()) {
                return null;
            }

            String rsPath = extractObjectId(testObject);
            String origLocator = extractLocator(testObject);
            String rawHint = (hint != null && !hint.isEmpty()) ? hint : rsPath;
            String effectiveHint = resolveEffectiveHint(testObject, rawHint, origLocator);

            HealContext context = HealContext.builder()
                    .targetHint(effectiveHint)
                    .action("VERIFY_PRESENT")
                    .value("")
                    .targetRsPath(rsPath)
                    .screenName(deriveScreenName(rsPath))
                    .originalLocator(origLocator)
                    .expectedTag(extractExpectedTag(rsPath))
                    .build();

            List<com.qureon.locara.core.model.UIElementNode> nodes = safeParsePageSource(pageSource);
            HealResult result = ENGINE.heal(nodes, pageSource, context);

            // Tier 2: Semantic Intent Matcher Fallback for banking error dialogs/text
            if (!result.isSuccess() && nodes != null && !nodes.isEmpty()) {
                com.qureon.locara.core.model.UIElementNode bestNode = null;
                double bestScore = 0.0;

                for (com.qureon.locara.core.model.UIElementNode node : nodes) {
                    if (!node.isDisplayed()) continue;
                    if (com.qureon.locara.core.guard.DangerKeywordGuard.isPolarityViolation(effectiveHint, node)) continue;
                    if (node.hasText()) {
                        double score = com.qureon.locara.core.matcher.LocaraIntentMatcher.calculateIntentScore(node.getText(), effectiveHint);
                        if (score > bestScore && score >= Math.max(0.85, ENGINE.getConfig().getMinConfidenceThreshold())) {
                            bestScore = score;
                            bestNode = node;
                        }
                    }
                }

                if (bestNode != null) {
                    String generatedXPath = XPATH_GENERATOR.generateOptimalXPath(bestNode, nodes, "VERIFY_PRESENT");
                    com.qureon.locara.core.model.HealCandidate candidate = new com.qureon.locara.core.model.HealCandidate(bestNode, generatedXPath, bestScore, "SEMANTIC_INTENT_MATCH");
                    result = new HealResult(true, candidate, null, "Healed via Semantic Intent Matching (" + String.format(java.util.Locale.US, "%.1f%%", bestScore * 100.0) + ")");
                }
            }

            // Note: Presence checks (waitForElementPresent, verifyElementPresent, isElementPresent)
            // are strictly read-only probes. They MUST NEVER mutate or scroll the viewport.

            if (!result.isSuccess() || result.getCandidate() == null) {
                return null;
            }

            String healedXPath = result.getCandidate().getSuggestedXPath();
            if (healedXPath == null || healedXPath.isEmpty()) {
                return null;
            }

            System.out.println("[Locara by Qureon] 🌟 HEALED PRESENT: " + hint + " -> XPath: " + healedXPath + " (Score: " +
                    String.format(java.util.Locale.US, "%.1f%%", result.getCandidate().getConfidenceScore() * 100.0) + ")");

            if (result.getGeneratedRsFile() != null) {
                System.out.println("[Locara by Qureon] 📝 Object Repository (.rs) written to: " + result.getGeneratedRsFile().getAbsolutePath());
            }

            if (testObject != null) {
                KatalonDriverBridge.patchTestObject(testObject, result.getCandidate().getNode(), healedXPath);
            }

            return healedXPath;
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] Probing heal error: " + t.getMessage());
            return null;
        }
    }

    /**
     * Non-fatal conditional status probe (aligning with Katalon 11.2 - 11.7+ isElement*).
     * Returns true if element is present or can be healed; returns false without throwing fatal exceptions.
     */
    public static boolean isElementPresent(Object testObject, String hint) {
        return isElementPresent(testObject, 10, hint);
    }

    public static boolean isElementPresent(Object testObject, int timeoutSeconds, String hint) {
        try {
            return healAndVerifyPresent(testObject, timeoutSeconds, hint);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Strict checkpoint assertion (aligning with Katalon 11.2 - 11.7+ assertElement*).
     * Heals element if needed; throws AssertionError if element is missing.
     */
    public static boolean assertElementPresent(Object testObject, String hint) {
        return assertElementPresent(testObject, 10, hint);
    }

    public static boolean assertElementPresent(Object testObject, int timeoutSeconds, String hint) {
        boolean ok = healAndVerifyPresent(testObject, timeoutSeconds, hint);
        if (!ok) {
            throw new AssertionError("[Locara by Qureon Assertion Failed] Element not present on screen: " + hint);
        }
        return true;
    }

    private static HealContext buildHealContext(Object testObject,
                                              String hint,
                                              String action,
                                              String value,
                                              String explicitRsPath) {
        String rsPath = explicitRsPath != null ? explicitRsPath : extractObjectId(testObject);
        String origLocator = extractLocator(testObject);
        String rawHint = (hint != null && !hint.isEmpty()) ? hint : rsPath;
        String effectiveHint = resolveEffectiveHint(testObject, rawHint, origLocator);

        return HealContext.builder()
                .targetHint(effectiveHint)
                .action(action)
                .value(value)
                .targetRsPath(rsPath)
                .screenName(deriveScreenName(rsPath))
                .originalLocator(origLocator)
                .expectedTag(extractExpectedTag(rsPath))
                .build();
    }

    private static List<com.qureon.locara.core.model.UIElementNode> safeParsePageSource(String pageSource) {
        try {
            return ENGINE.parsePageSource(pageSource);
        } catch (Exception ignored) {
            return java.util.Collections.emptyList();
        }
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

        HealContext context = buildHealContext(testObject, hint, action, value, explicitRsPath);
        String rsPath = context.getTargetRsPath();
        String effectiveHint = context.getTargetHint();

        List<com.qureon.locara.core.model.UIElementNode> nodes = safeParsePageSource(pageSource);
        HealResult result = ENGINE.heal(nodes, pageSource, context);

        // Overlay Airbag Fallback: If target not found, check for blocking OS permission or modal overlay
        if (!result.isSuccess()) {
            boolean dismissed = OVERLAY_AIRBAG.dismissBlockingOverlay(driver, nodes);
            if (dismissed) {
                pageSource = KatalonDriverBridge.getPageSource(driver);
                if (pageSource != null && !pageSource.isEmpty()) {
                    nodes = safeParsePageSource(pageSource);
                    result = ENGINE.heal(nodes, pageSource, context);
                }
            }
        }

        // Auto-Scroll Fallback: If not found, attempt scroll recovery on scrollable containers
        ScrollRecoveryEngine activeScrollEngine = getScrollEngine();
        if (!result.isSuccess() && ENGINE.getConfig().isScrollRecoveryEnabled() && activeScrollEngine.getMaxScrollAttempts() > 0) {
            try {
                if (activeScrollEngine.isScrollEligible(effectiveHint, rsPath, action, nodes)) {
                    for (int attempt = 1; attempt <= activeScrollEngine.getMaxScrollAttempts(); attempt++) {
                        System.out.println("[Locara by Qureon] 📜 Target '" + hint + "' not found in active viewport. Attempting auto-scroll recovery #" + attempt + "...");
                        boolean scrolled = activeScrollEngine.performScrollDown(driver);
                        if (!scrolled) break;

                        pageSource = KatalonDriverBridge.getPageSource(driver);
                        if (pageSource != null && !pageSource.isEmpty()) {
                            result = ENGINE.heal(safeParsePageSource(pageSource), pageSource, context);
                            if (result.isSuccess()) {
                                System.out.println("[Locara by Qureon] 🎯 Target '" + hint + "' successfully discovered after scroll recovery!");
                                break;
                            }
                        }
                    }
                } else if (activeScrollEngine.isOverlayOrDialogTarget(effectiveHint, rsPath)) {
                    System.out.println("[Locara by Qureon] 🛡️ Target '" + hint + "' identified as Transient Popup / Overlay / Permission Dialog. Auto-scroll strictly inhibited to preserve screen state.");
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
        return executeDirectDriverAction(driver, healedXPath, action, value, timeoutSeconds);
    }

    private static boolean executeDirectDriverAction(Object driver, String xpath, String action, String value, int timeoutSeconds) {
        try {
            Object element = findElementWithTimeout(driver, xpath, timeoutSeconds);

            if ("TAP".equalsIgnoreCase(action) || "CLICK".equalsIgnoreCase(action)) {
                Method clickMethod = element.getClass().getMethod("click");
                clickMethod.invoke(element);
                return true;
            } else if ("SET_TEXT".equalsIgnoreCase(action)) {
                Method clearMethod = element.getClass().getMethod("clear");
                clearMethod.invoke(element);
                Method sendKeysMethod = element.getClass().getMethod("sendKeys", CharSequence[].class);
                sendKeysMethod.invoke(element, (Object) new CharSequence[]{value != null ? value : ""});
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

    private static String executeDirectGetText(Object driver, String xpath, int timeoutSeconds) {
        try {
            Object element = findElementWithTimeout(driver, xpath, timeoutSeconds);

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

    private static Class<?> getByClass() throws ClassNotFoundException {
        Class<?> cls = cachedByClass;
        if (cls == null) {
            cls = Class.forName("org.openqa.selenium.By");
            cachedByClass = cls;
        }
        return cls;
    }

    private static Object createByXPath(String xpath) throws Exception {
        Method m = cachedByXPathMethod;
        if (m == null) {
            m = getByClass().getMethod("xpath", String.class);
            cachedByXPathMethod = m;
        }
        return m.invoke(null, xpath);
    }

    private static List<?> findElementsByXPath(Object driver, String xpath) throws Exception {
        Object by = createByXPath(xpath);
        Method findElementsMethod = driver.getClass().getMethod("findElements", getByClass());
        Object result = findElementsMethod.invoke(driver, by);
        return result instanceof List ? (List<?>) result : java.util.Collections.emptyList();
    }

    private static Object findElementWithTimeout(Object driver, String xpath, int timeoutSeconds) throws Exception {
        long maxWaitMs = Math.max(0, timeoutSeconds) * 1000L;
        long deadline = System.currentTimeMillis() + maxWaitMs;
        Object by = createByXPath(xpath);
        Class<?> byClass = getByClass();
        Method findElementMethod = driver.getClass().getMethod("findElement", byClass);
        Exception lastException = null;

        while (true) {
            try {
                return findElementMethod.invoke(driver, by);
            } catch (Exception e) {
                lastException = e;
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw lastException;
                }
                try {
                    Thread.sleep(Math.min(250L, remaining));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw lastException;
                }
            }
        }
    }

    private static Class<?> getMobileKeywordsClass() throws ClassNotFoundException {
        Class<?> cls = cachedMobileKeywordsClass;
        if (cls == null) {
            cls = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
            cachedMobileKeywordsClass = cls;
        }
        return cls;
    }

    private static Class<?> getTestObjectClass() throws ClassNotFoundException {
        Class<?> cls = cachedTestObjectClass;
        if (cls == null) {
            cls = Class.forName("com.kms.katalon.core.testobject.TestObject");
            cachedTestObjectClass = cls;
        }
        return cls;
    }

    private static Method getMobileKeywordMethod(String name, Class<?>... parameterTypes) throws Exception {
        return getMobileKeywordsClass().getMethod(name, parameterTypes);
    }

    private static boolean invokeKatalonTap(Object testObject, int timeout) throws Exception {
        Method tapMethod = getMobileKeywordMethod("tap", getTestObjectClass(), int.class);
        tapMethod.invoke(null, testObject, timeout);
        return true;
    }

    private static boolean invokeKatalonSetText(Object testObject, String text, int timeout) throws Exception {
        Method setTextMethod = getMobileKeywordMethod("setText", getTestObjectClass(), String.class, int.class);
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

    public static String extractLocator(Object testObject) {
        if (testObject == null) return "";
        for (String methodName : new String[]{"getMobileLocator", "getLocator"}) {
            try {
                Method m = testObject.getClass().getMethod(methodName);
                Object val = m.invoke(testObject);
                if (val != null && !val.toString().trim().isEmpty()) {
                    return val.toString().trim();
                }
            } catch (Exception ignored) {}
        }
        try {
            Method getXpath = testObject.getClass().getMethod("findPropertyValue", String.class, boolean.class);
            Object val = getXpath.invoke(testObject, "xpath", false);
            return val != null ? val.toString() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String resolveEffectiveHint(Object testObject, String rawHint, String originalLocator) {
        String cleaned = com.qureon.locara.core.matcher.HeuristicMatcher.cleanSemanticHint(rawHint);
        if (!isGenericOrEmptyHint(cleaned)) {
            return cleaned;
        }
        // Fallback 1: Inspect TestObject properties (text, content-desc, resource-id)
        if (testObject != null) {
            for (String propName : new String[]{"text", "content-desc", "resource-id"}) {
                try {
                    Method findProp = testObject.getClass().getMethod("findPropertyValue", String.class, boolean.class);
                    Object val = findProp.invoke(testObject, propName, false);
                    if (val != null && !val.toString().trim().isEmpty()) {
                        String propVal = val.toString().trim();
                        if ("resource-id".equals(propName) && propVal.contains(":id/")) {
                            propVal = propVal.substring(propVal.indexOf(":id/") + 4);
                        }
                        if (!isGenericOrEmptyHint(propVal)) {
                            return propVal;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        // Fallback 2: Extract @text='...' or @content-desc='...' from originalLocator
        if (originalLocator != null && !originalLocator.isEmpty()) {
            Matcher m = LOCATOR_ATTR_PATTERN.matcher(originalLocator);
            if (m.find()) {
                String extracted = m.group(1).trim();
                if (!isGenericOrEmptyHint(extracted)) {
                    return extracted;
                }
            }
        }
        return !cleaned.isEmpty() ? cleaned : (rawHint != null ? rawHint : "");
    }

    private static boolean isGenericOrEmptyHint(String s) {
        if (s == null || s.trim().isEmpty()) return true;
        String lower = s.trim().toLowerCase();
        return lower.equals("button") || lower.equals("tombol") || lower.equals("viewgroup") ||
                lower.equals("groupview") || lower.equals("textview") || lower.equals("edittext") ||
                lower.equals("imageview") || lower.equals("checkbox") || lower.equals("radiobutton") ||
                lower.equals("view") || lower.equals("input") || lower.equals("field") ||
                lower.equals("text") || lower.equals("value") || lower.equals("label");
    }

    private static String extractExpectedTag(String rsPath) {
        if (rsPath == null || rsPath.isEmpty()) return null;
        String name = rsPath.replace("\\", "/");
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        Matcher matcher = EXPECTED_TAG_PATTERN.matcher(name);
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

    private static boolean verifyElementExists(Object driver, String xpath) {
        if (driver == null || xpath == null || xpath.isEmpty()) {
            return false;
        }
        try {
            List<?> elements = findElementsByXPath(driver, xpath);
            return elements != null && !elements.isEmpty();
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] Element existence verification failed: " + t.getMessage());
            return false;
        }
    }

    private static boolean verifyElementDisplayed(Object driver, String xpath) {
        if (driver == null || xpath == null || xpath.isEmpty()) {
            return false;
        }
        try {
            List<?> elements = findElementsByXPath(driver, xpath);
            if (elements != null && !elements.isEmpty()) {
                Object el = elements.get(0);
                Method isDisplayed = el.getClass().getMethod("isDisplayed");
                Object visible = isDisplayed.invoke(el);
                return Boolean.TRUE.equals(visible);
            }
            return false;
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] Element visibility verification failed: " + t.getMessage());
            return false;
        }
    }

    /**
     * Dynamically resolves the active bank account card from the current mobile screen.
     */
    public static com.qureon.locara.core.context.ResolvedAccount resolveActiveAccount(Object driver) {
        if (driver == null) {
            driver = KatalonDriverBridge.getMobileDriver();
        }
        if (driver == null) {
            return null;
        }
        String pageSource = KatalonDriverBridge.getPageSource(driver);
        return CONTEXT_RESOLVER.resolveActiveAccount(pageSource);
    }

    /**
     * Resolves the active bank account card directly from raw XML/HTML page source string.
     */
    public static com.qureon.locara.core.context.ResolvedAccount resolveActiveAccount(String pageSource) {
        return CONTEXT_RESOLVER.resolveActiveAccount(pageSource);
    }

    /**
     * Dynamically resolves a specific bank account card matching target account number from current mobile screen.
     */
    public static com.qureon.locara.core.context.ResolvedAccount resolveAccountByNumber(Object driver, String targetAccountNumber) {
        if (driver == null) {
            driver = KatalonDriverBridge.getMobileDriver();
        }
        if (driver == null) {
            return null;
        }
        String pageSource = KatalonDriverBridge.getPageSource(driver);
        return CONTEXT_RESOLVER.resolveAccountByNumber(pageSource, targetAccountNumber);
    }

    public static com.qureon.locara.core.context.ResolvedAccount resolveAccountByNumber(String targetAccountNumber) {
        return resolveAccountByNumber(null, targetAccountNumber);
    }

    /**
     * Synthesizes an optimal, safe XPath to tap the bank account card avoiding copy and eye button interception.
     */
    public static String getSafeCardTapXPath(Object driver, String targetAccountNumber) {
        if (driver == null) {
            driver = KatalonDriverBridge.getMobileDriver();
        }
        String pageSource = driver != null ? KatalonDriverBridge.getPageSource(driver) : "";
        return CONTEXT_RESOLVER.resolveSafeCardTapXPath(pageSource, targetAccountNumber);
    }

    public static String getSafeCardTapXPath(String targetAccountNumber) {
        return getSafeCardTapXPath(null, targetAccountNumber);
    }

    /**
     * Autonomously locates and safely taps the bank account card corresponding to targetAccountNumber.
     */
    public static boolean tapAccountCard(Object driver, String targetAccountNumber) {
        if (driver == null) {
            driver = KatalonDriverBridge.getMobileDriver();
        }
        if (driver == null) {
            return false;
        }
        String xpath = getSafeCardTapXPath(driver, targetAccountNumber);
        if (xpath == null || xpath.isEmpty()) {
            return false;
        }
        try {
            List<?> elements = findElementsByXPath(driver, xpath);
            if (elements != null && !elements.isEmpty()) {
                Object el = elements.get(0);
                Method clickMethod = el.getClass().getMethod("click");
                clickMethod.invoke(el);
                System.out.println("[Locara by Qureon] 💳 Safely tapped account card via: " + xpath);
                return true;
            }
        } catch (Throwable t) {
            System.out.println("[Locara by Qureon] Warning: tapAccountCard direct click failed: " + t.getMessage());
        }
        return false;
    }

    public static boolean tapAccountCard(String targetAccountNumber) {
        return tapAccountCard(null, targetAccountNumber);
    }

    /**
     * Synthesizes and injects Menu Variables into a Katalon Test Case XML Entity (.tc).
     */
    public static boolean synthesizeTestCaseVariables(File tcFile, java.util.List<com.qureon.locara.katalon.variable.TestCaseVariable> variables) {
        try {
            return VARIABLE_SYNTHESIZER.synthesizeVariables(tcFile, variables);
        } catch (Exception e) {
            System.err.println("[Locara] Failed to synthesize variables: " + e.getMessage());
            return false;
        }
    }

    /**
     * Synchronizes a specific variable default value into a .tc file.
     */
    public static boolean syncVariableDefault(File tcFile, String varName, String defaultValue) {
        try {
            return VARIABLE_SYNTHESIZER.syncVariableDefault(tcFile, varName, defaultValue);
        } catch (Exception e) {
            System.err.println("[Locara] Failed to sync variable default: " + e.getMessage());
            return false;
        }
    }

    /**
     * Aligns a Groovy script to ensure variables are declared outside try-catch blocks.
     */
    public static boolean alignScript(File scriptFile) {
        try {
            return SCRIPT_ALIGNER.alignScript(scriptFile);
        } catch (Exception e) {
            System.err.println("[Locara] Failed to align script: " + e.getMessage());
            return false;
        }
    }
}
