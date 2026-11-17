package com.qureon.locara.katalon;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.context.ResolvedAccount;
import com.qureon.locara.core.driver.AppiumDriverActionBridge;
import com.qureon.locara.core.generator.DynamicXPathGenerator;
import com.qureon.locara.core.guard.OverlayAirbag;
import com.qureon.locara.core.logging.LocaraLogger;
import com.qureon.locara.core.model.AppTarget;
import com.qureon.locara.katalon.variable.TestCaseVariable;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Public User API facade for Katalon Studio Test Cases and Screen Flows.
 * Preserves 100% backward-compatible signatures while delegating execution to modular coordinators.
 */
public class Locara {

    private static final String LOG_PREFIX = "[Locara by Qureon]";
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private static final LocaraEngine ENGINE = KatalonProjectToolkit.createDefaultEngine();
    private static final KeyboardGuard KEYBOARD_GUARD = new KeyboardGuard();
    private static final KatalonHealingCoordinator COORDINATOR = new KatalonHealingCoordinator(
            ENGINE, KEYBOARD_GUARD, new OverlayAirbag(), new DynamicXPathGenerator()
    );

    private static volatile ScrollRecoveryEngine scrollEngine =
            new ScrollRecoveryEngine(ENGINE.getConfig().getMaxScrollAttempts());
    private static volatile AppTarget defaultTarget = AppTarget.AUTO;
    private static final ThreadLocal<AppTarget> threadTarget = new ThreadLocal<>();
    private static volatile Class<?> cachedMobileKeywordsClass;
    private static volatile Class<?> cachedTestObjectClass;

    public static void setAppTarget(AppTarget target) {
        AppTarget active = target != null ? target : AppTarget.AUTO;
        defaultTarget = active;
        threadTarget.remove();
        ENGINE.getConfig().setAppTarget(active);
        LocaraLogger.info(LOG_PREFIX, "Active Target Profile: " + active.getDescription());
    }

    public static void setThreadAppTarget(AppTarget target) {
        if (target == null) { clearThreadAppTarget(); return; }
        threadTarget.set(target);
        ENGINE.getConfig().setThreadAppTarget(target);
    }

    public static void clearThreadAppTarget() {
        threadTarget.remove();
        ENGINE.getConfig().clearThreadAppTarget();
    }

    public static AppTarget getAppTarget() {
        AppTarget target = threadTarget.get();
        if (target != null) return target;
        AppTarget cfgTarget = ENGINE.getConfig().getAppTarget();
        return cfgTarget != null ? cfgTarget : defaultTarget;
    }

    public static void resetSessionContext() {
        threadTarget.remove();
        ManualScrollGuard.clear();
        ENGINE.resetSessionContext();
    }

    public static void setProfileUmum() { setAppTarget(AppTarget.UMUM); }
    public static void setProfileRevamp() { setAppTarget(AppTarget.REVAMP); }
    public static void setProfileKmp() { setAppTarget(AppTarget.KMP); }
    public static void setProfileAuto() { setAppTarget(AppTarget.AUTO); }

    public static void setAutoGenerateRs(boolean enable) { ENGINE.getConfig().setAutoGenerateRs(enable); }
    public static void setAllowOverwriteRs(boolean enable) { ENGINE.getConfig().setAllowOverwriteExistingRs(enable); }
    public static void setScrollRecoveryEnabled(boolean enable) { ENGINE.getConfig().setScrollRecoveryEnabled(enable); }
    public static boolean isScrollRecoveryEnabled() { return ENGINE.getConfig().isScrollRecoveryEnabled(); }

    public static void setMaxScrollAttempts(int maxAttempts) {
        ENGINE.getConfig().setMaxScrollAttempts(maxAttempts);
        scrollEngine = new ScrollRecoveryEngine(ENGINE.getConfig().getMaxScrollAttempts());
    }

    public static void notifyManualScroll() { ManualScrollGuard.recordManualScroll(); }
    public static void clearManualScrollState() { ManualScrollGuard.clear(); }
    public static void setManualScrollGuardEnabled(boolean enable) { ENGINE.getConfig().setManualScrollGuardEnabled(enable); }
    public static boolean isManualScrollGuardEnabled() { return ENGINE.getConfig().isManualScrollGuardEnabled(); }
    public static void setManualScrollCooldownMs(long cooldownMs) { ENGINE.getConfig().setManualScrollCooldownMs(cooldownMs); }
    public static ManualScrollGuard.ActionWindow openActionWindow() { return ManualScrollGuard.openActionWindow(ENGINE.getConfig()); }
    public static void recordSuccessfulTap() { ManualScrollGuard.markActionTapSucceeded(); }

    public static LocaraEngine getEngine() { return ENGINE; }
    public static KeyboardGuard getKeyboardGuard() { return KEYBOARD_GUARD; }

    public static ScrollRecoveryEngine getScrollEngine() {
        int configured = ENGINE.getConfig().getMaxScrollAttempts();
        if (scrollEngine.getMaxScrollAttempts() != configured) {
            scrollEngine = new ScrollRecoveryEngine(configured);
        }
        return scrollEngine;
    }

    public static boolean tap(Object testObject, String hint) { return tap(testObject, DEFAULT_TIMEOUT_SECONDS, hint); }

    public static boolean tap(Object testObject, int timeoutSeconds, String hint) {
        try (ManualScrollGuard.ActionWindow ignored = openActionWindow()) {
            try {
                invokeKatalonKeyword("tap", new Class<?>[]{getTestObjectClass(), int.class}, testObject, timeoutSeconds);
                ManualScrollGuard.markActionTapSucceeded();
                return true;
            } catch (Throwable t) {
                if (!ENGINE.getConfig().isEnabled()) return false;
                LocaraLogger.info(LOG_PREFIX, "Intercepted tap failure on: " + hint + " (" + t.getMessage() + ")");
            }
            return COORDINATOR.executeHealing(testObject, hint, "TAP", "", timeoutSeconds, null, getScrollEngine());
        }
    }

    public static boolean healAndTap(Object testObject, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        try (ManualScrollGuard.ActionWindow ignored = openActionWindow()) {
            return COORDINATOR.executeHealing(testObject, hint, "TAP", "", timeoutSeconds, null, getScrollEngine());
        }
    }

    public static boolean tapByHint(String hint, String targetRsPath) { return tapByHint(hint, DEFAULT_TIMEOUT_SECONDS, targetRsPath); }

    public static boolean tapByHint(String hint, int timeoutSeconds, String targetRsPath) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        try (ManualScrollGuard.ActionWindow ignored = openActionWindow()) {
            return COORDINATOR.executeHealing(null, hint, "TAP", "", timeoutSeconds, targetRsPath, getScrollEngine());
        }
    }

    public static boolean setText(Object testObject, String text, String hint) { return setText(testObject, text, DEFAULT_TIMEOUT_SECONDS, hint); }

    public static boolean setText(Object testObject, String text, int timeoutSeconds, String hint) {
        try (ManualScrollGuard.ActionWindow ignored = openActionWindow()) {
            try {
                invokeKatalonKeyword("setText", new Class<?>[]{getTestObjectClass(), String.class, int.class}, testObject, text, timeoutSeconds);
                return true;
            } catch (Throwable t) {
                if (!ENGINE.getConfig().isEnabled()) return false;
                LocaraLogger.info(LOG_PREFIX, "Intercepted setText failure on: " + hint + " (" + t.getMessage() + ")");
            }
            return COORDINATOR.executeHealing(testObject, hint, "SET_TEXT", text, timeoutSeconds, null, getScrollEngine());
        }
    }

    public static boolean healAndSetText(Object testObject, String text, String hint) { return healAndSetText(testObject, text, DEFAULT_TIMEOUT_SECONDS, hint); }

    public static boolean healAndSetText(Object testObject, String text, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        try (ManualScrollGuard.ActionWindow ignored = openActionWindow()) {
            return COORDINATOR.executeHealing(testObject, hint, "SET_TEXT", text, timeoutSeconds, null, getScrollEngine());
        }
    }

    public static String getText(Object testObject, String hint) { return getText(testObject, DEFAULT_TIMEOUT_SECONDS, hint); }

    public static String getText(Object testObject, int timeoutSeconds, String hint) {
        try {
            Object result = invokeKatalonKeyword("getText", new Class<?>[]{getTestObjectClass(), int.class}, testObject, timeoutSeconds);
            if (result != null) return result.toString();
        } catch (Throwable t) {
            LocaraLogger.info(LOG_PREFIX, "Intercepted getText failure on: " + hint + " (" + t.getMessage() + ")");
        }
        return ENGINE.getConfig().isEnabled() ? healAndGetText(testObject, timeoutSeconds, hint) : "";
    }

    public static String healAndGetText(Object testObject, int timeoutSeconds, String hint) {
        return COORDINATOR.healAndGetText(testObject, timeoutSeconds, hint);
    }

    public static boolean clearText(Object testObject, String hint) { return clearText(testObject, DEFAULT_TIMEOUT_SECONDS, hint); }

    public static boolean clearText(Object testObject, int timeoutSeconds, String hint) {
        try (ManualScrollGuard.ActionWindow ignored = openActionWindow()) {
            try {
                invokeKatalonKeyword("clearText", new Class<?>[]{getTestObjectClass(), int.class}, testObject, timeoutSeconds);
                return true;
            } catch (Throwable t) {
                LocaraLogger.info(LOG_PREFIX, "Intercepted clearText failure on: " + hint + " (" + t.getMessage() + ")");
            }
            return ENGINE.getConfig().isEnabled() && COORDINATOR.executeHealing(testObject, hint, "CLEAR_TEXT", "", timeoutSeconds, null, getScrollEngine());
        }
    }

    public static boolean healAndClearText(Object testObject, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        try (ManualScrollGuard.ActionWindow ignored = openActionWindow()) {
            return COORDINATOR.executeHealing(testObject, hint, "CLEAR_TEXT", "", timeoutSeconds, null, getScrollEngine());
        }
    }

    public static boolean healAndVerifyPresent(Object testObject, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        Object driver = KatalonDriverBridge.getMobileDriver();
        String xpath = COORDINATOR.resolvePresentXPathAndPatch(driver, testObject, hint);
        return xpath != null && !xpath.isEmpty() && AppiumDriverActionBridge.verifyElementExists(driver, xpath);
    }

    public static boolean healAndVerifyVisible(Object testObject, int timeoutSeconds, String hint) {
        if (!ENGINE.getConfig().isEnabled()) return false;
        Object driver = KatalonDriverBridge.getMobileDriver();
        String xpath = COORDINATOR.resolvePresentXPathAndPatch(driver, testObject, hint);
        return xpath != null && !xpath.isEmpty() && AppiumDriverActionBridge.verifyElementDisplayed(driver, xpath);
    }

    public static boolean isElementPresent(Object testObject, String hint) { return isElementPresent(testObject, DEFAULT_TIMEOUT_SECONDS, hint); }

    public static boolean isElementPresent(Object testObject, int timeoutSeconds, String hint) {
        try { return healAndVerifyPresent(testObject, timeoutSeconds, hint); } catch (Throwable t) { return false; }
    }

    public static boolean assertElementPresent(Object testObject, String hint) { return assertElementPresent(testObject, DEFAULT_TIMEOUT_SECONDS, hint); }

    public static boolean assertElementPresent(Object testObject, int timeoutSeconds, String hint) {
        if (!healAndVerifyPresent(testObject, timeoutSeconds, hint)) {
            throw new AssertionError("[Locara by Qureon Assertion Failed] Element not present on screen: " + hint);
        }
        return true;
    }

    public static String extractLocator(Object testObject) { return KatalonObjectExtractor.extractLocator(testObject); }
    public static String resolveEffectiveHint(Object testObject, String rawHint, String orig) { return KatalonObjectExtractor.resolveEffectiveHint(testObject, rawHint, orig); }

    public static ResolvedAccount resolveAccountByNumber(Object driver, String target) { return KatalonProjectToolkit.resolveAccountByNumber(driver, target); }
    public static ResolvedAccount resolveAccountByNumber(String target) { return resolveAccountByNumber(null, target); }
    public static String getSafeCardTapXPath(Object driver, String target) { return KatalonProjectToolkit.getSafeCardTapXPath(driver, target); }
    public static String getSafeCardTapXPath(String target) { return getSafeCardTapXPath(null, target); }
    public static boolean tapAccountCard(Object driver, String target) { return KatalonProjectToolkit.tapAccountCard(driver, target); }
    public static boolean tapAccountCard(String target) { return tapAccountCard(null, target); }
    public static boolean synthesizeTestCaseVariables(File tc, List<TestCaseVariable> vars) { return KatalonProjectToolkit.synthesizeTestCaseVariables(tc, vars); }
    public static boolean syncVariableDefault(File tc, String varName, String def) { return KatalonProjectToolkit.syncVariableDefault(tc, varName, def); }
    public static boolean alignScript(File scriptFile) { return KatalonProjectToolkit.alignScript(scriptFile); }

    private static Object invokeKatalonKeyword(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Class<?> cls = cachedMobileKeywordsClass;
        if (cls == null) {
            cls = Class.forName("com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords");
            cachedMobileKeywordsClass = cls;
        }
        Method m = cls.getMethod(name, paramTypes);
        return m.invoke(null, args);
    }

    private static Class<?> getTestObjectClass() throws ClassNotFoundException {
        Class<?> cls = cachedTestObjectClass;
        if (cls == null) {
            cls = Class.forName("com.kms.katalon.core.testobject.TestObject");
            cachedTestObjectClass = cls;
        }
        return cls;
    }
}
