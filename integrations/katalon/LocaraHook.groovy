package core

import java.lang.reflect.Field

import com.kms.katalon.core.configuration.RunConfiguration
import com.kms.katalon.core.constants.CoreConstants
import com.kms.katalon.core.exception.StepFailedException
import com.kms.katalon.core.keyword.internal.IKeyword
import com.kms.katalon.core.keyword.internal.KeywordExecutor
import com.kms.katalon.core.keyword.internal.SupportLevel
import com.kms.katalon.core.mobile.keyword.builtin.GetTextKeyword
import com.kms.katalon.core.mobile.keyword.builtin.SetTextKeyword
import com.kms.katalon.core.mobile.keyword.builtin.TapKeyword
import com.kms.katalon.core.model.FailureHandling
import com.kms.katalon.core.testobject.TestObject
import com.kms.katalon.core.util.KeywordUtil

/**
 * Native KeywordExecutor interceptor for Katalon Studio (Ghost Mode compatible).
 */
class LocaraHook {
    private static final double MIN_SEMANTIC_TEXT_THRESHOLD = 0.80d
    private static final int DEFAULT_TIMEOUT_SECONDS = 10
    private static final List<String> CONFIG_CANDIDATES = [
        "locara.local.properties", "qureon.local.properties", "locara.properties", "qureon.properties",
        "Include/config/locara.properties", "Include/config/qureon.properties"
    ]
    private static volatile boolean installed = false
    private static volatile Class<?> cachedLocaraClass = null
    private static volatile boolean checkedLocara = false

    static Class<?> getLocaraClass() {
        if (!checkedLocara) {
            try { cachedLocaraClass = Class.forName("com.qureon.locara.katalon.Locara") } catch (Throwable ignored) { cachedLocaraClass = null }
            checkedLocara = true
        }
        return cachedLocaraClass
    }

    static boolean isLocaraPresent() { return getLocaraClass() != null }

    static boolean isHealingEnabled() {
        if (!isLocaraPresent()) return false
        Boolean cfg = readBooleanFlag(["ENABLE_LOCARA", "ENABLE_QUREON", "ENABLE_ZERO_HEALING"], ["locara.enabled", "qureon.enabled", "healing.enabled"], ["LOCARA_ENABLED", "QUREON_ENABLED", "HEALING_ENABLED"])
        return cfg != null ? cfg.booleanValue() : true
    }

    static boolean isWaitPresentHealingEnabled() {
        Boolean cfg = readBooleanFlag(["LOCARA_HOOK_HEAL_WAIT_PRESENT"], ["locara.hook.heal_wait_present"], ["LOCARA_HOOK_HEAL_WAIT_PRESENT"])
        return cfg != null && cfg.booleanValue()
    }

    private static Boolean readBooleanFlag(List<String> gvNames, List<String> propKeys, List<String> envKeys) {
        try {
            def gvClass = Class.forName("internal.GlobalVariable")
            for (String f : gvNames) { try { Object val = gvClass.getField(f)?.get(null); if (val != null) return Boolean.parseBoolean(val.toString().trim()) } catch (Throwable ignored) {} }
        } catch (Throwable ignored) {}
        for (String k : envKeys) { String env = System.getenv(k); if (env != null && !env.trim().isEmpty()) return Boolean.parseBoolean(env.trim()) }
        for (String k : propKeys) { String sys = System.getProperty(k); if (sys != null && !sys.trim().isEmpty()) return Boolean.parseBoolean(sys.trim()) }
        try {
            String userDir = System.getProperty("user.dir")
            for (String fn : CONFIG_CANDIDATES) {
                File f = new File(userDir, fn)
                if (f.exists()) {
                    Properties p = new Properties(); f.withReader("UTF-8") { p.load(it) }
                    for (String k : propKeys) { String v = p.getProperty(k); if (v != null && !v.trim().isEmpty()) return Boolean.parseBoolean(v.trim()) }
                }
            }
        } catch (Throwable ignored) {}
        return null
    }

    static boolean isNegativeTestCaseRunning() {
        try {
            String tcId = Class.forName("GlobalSetupListener").getField("currentTestCaseId").get(null)?.toString() ?: ""
            return tcId.contains("Negative") || tcId.contains("/TC-N") || tcId.contains("NTC -") || tcId.contains("/NTC")
        } catch (Throwable ignored) { return false }
    }

    static double getConfiguredConfidenceThreshold(double minimumFloor) {
        try {
            Object cfg = getLocaraClass()?.getMethod("getEngine")?.invoke(null)?.getClass()?.getMethod("getConfig")?.invoke(getLocaraClass()?.getMethod("getEngine")?.invoke(null))
            if (cfg != null) return Math.max(minimumFloor, ((Number) cfg.getClass().getMethod("getMinConfidenceThreshold").invoke(cfg)).doubleValue())
        } catch (Throwable ignored) {}
        return minimumFloor
    }

    static synchronized void install() {
        if (installed || !isLocaraPresent()) return
        if (!isHealingEnabled()) { KeywordUtil.logInfo("[Locara by Qureon] Engine DISABLED via configuration. Preserving 100% native Katalon execution."); return }
        try {
            KeywordExecutor executor = KeywordExecutor.getInstance(CoreConstants.PLATFORM_MOBILE)
            Field cacheField = KeywordExecutor.class.getDeclaredField("cachePlatforms"); cacheField.setAccessible(true)
            @SuppressWarnings("unchecked")
            Map<String, List<IKeyword>> mobileCache = ((Map<String, Map<String, List<IKeyword>>>) cacheField.get(executor))
                    .computeIfAbsent(CoreConstants.PLATFORM_MOBILE, { new HashMap<String, List<IKeyword>>() })

            installInterceptor(mobileCache, "tap", { new TapKeyword() }, LocaraKeywordProxy.class, { IKeyword orig ->
                new LocaraKeywordProxy(orig, null, true, true) { p, t ->
                    TestObject to = extractTestObject(p); int timeout = extractTimeout(p, 1, DEFAULT_TIMEOUT_SECONDS)
                    healAndFailSafe(extractObjId(to), "Mobile.tap", extractFailureHandling(p, 2), t, null) { locara -> locara.healAndTap(to, timeout, extractObjId(to)) as boolean }
                }
            })
            installInterceptor(mobileCache, "setText", { new SetTextKeyword() }, LocaraKeywordProxy.class, { IKeyword orig ->
                new LocaraKeywordProxy(orig, null, true, false) { p, t ->
                    TestObject to = extractTestObject(p); String txt = p != null && p.length > 1 && p[1] != null ? p[1].toString() : ""
                    healAndFailSafe(extractObjId(to), "Mobile.setText", extractFailureHandling(p, 3), t, null) { locara -> locara.healAndSetText(to, txt, extractTimeout(p, 2, DEFAULT_TIMEOUT_SECONDS), extractObjId(to)) as boolean }
                }
            })
            installInterceptor(mobileCache, "getText", { new GetTextKeyword() }, LocaraKeywordProxy.class, { IKeyword orig ->
                new LocaraKeywordProxy(orig, "", false, false) { p, t -> handleGetText(p, t) }
            })
            installOptionalInterceptor(mobileCache, "verifyElementText", "com.kms.katalon.core.mobile.keyword.builtin.VerifyElementTextKeyword", LocaraKeywordProxy.class, { IKeyword orig ->
                new LocaraKeywordProxy(orig, false, false, false) { p, t -> handleVerifyElementText(p, t) }
            })
            installOptionalInterceptor(mobileCache, "waitForElementPresent", "com.kms.katalon.core.mobile.keyword.builtin.WaitForElementPresentKeyword", LocaraKeywordProxy.class, { IKeyword orig ->
                new LocaraProbeProxy(orig, true, false) { to, timeout, objId -> getLocaraClass()?.healAndVerifyPresent(to, timeout, objId) as boolean }
            })
            installOptionalInterceptor(mobileCache, "verifyElementPresent", "com.kms.katalon.core.mobile.keyword.builtin.VerifyElementPresentKeyword", LocaraKeywordProxy.class, { IKeyword orig ->
                new LocaraProbeProxy(orig, false, true, "not present") { to, timeout, objId -> getLocaraClass()?.healAndVerifyPresent(to, timeout, objId) as boolean }
            })
            installOptionalInterceptor(mobileCache, "verifyElementVisible", "com.kms.katalon.core.mobile.keyword.builtin.VerifyElementVisibleKeyword", LocaraKeywordProxy.class, { IKeyword orig ->
                new LocaraProbeProxy(orig, false, true, "not visible") { to, timeout, objId -> getLocaraClass()?.healAndVerifyVisible(to, timeout, objId) as boolean }
            })
            ["swipe": "SwipeKeyword", "swipeWithDuration": "SwipeWithDurationKeyword", "scrollToText": "ScrollToTextKeyword", "scrollToTextHorizontal": "ScrollToTextHorizontalKeyword", "enhancedScrollToText": "EnhancedScrollToTextKeyword"].each { k, cls ->
                installOptionalInterceptor(mobileCache, k, "com.kms.katalon.core.mobile.keyword.builtin.${cls}", LocaraManualScrollInterceptor.class, { IKeyword orig -> new LocaraManualScrollInterceptor(orig) })
            }
            installed = true
            KeywordUtil.logInfo("[Locara by Qureon] Native KeywordExecutor Hook v1.16.0 installed (actions, probes, and manual-scroll guard)")
        } catch (Throwable t) { KeywordUtil.markWarning("[Locara by Qureon] Failed to install KeywordExecutor hook: " + t.getMessage()) }
    }

    private static Object handleGetText(Object[] params, Throwable t) {
        TestObject to = extractTestObject(params); int timeout = extractTimeout(params, 1, DEFAULT_TIMEOUT_SECONDS); String objId = extractObjId(to)
        try {
            String healedText = getLocaraClass()?.healAndGetText(to, timeout, objId) as String
            if (healedText != null) return healedText
        } catch (Throwable healEx) { KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Healing failed for getText('${objId}'): " + healEx.getMessage()) }
        if (extractFailureHandling(params, 2) == FailureHandling.STOP_ON_FAILURE) throw t
        return ""
    }

    private static Object handleVerifyElementText(Object[] params, Throwable t) {
        TestObject to = extractTestObject(params); String expected = params != null && params.length > 1 && params[1] != null ? params[1].toString() : ""; FailureHandling flow = extractFailureHandling(params, 2)
        if (isNegativeTestCaseRunning()) { if (flow == FailureHandling.STOP_ON_FAILURE) throw t; return false }
        try {
            String actualText = getLocaraClass()?.healAndGetText(to, DEFAULT_TIMEOUT_SECONDS, extractObjId(to)) as String
            if (actualText != null && !expected.trim().isEmpty()) {
                String act = actualText.trim(), exp = expected.trim()
                boolean safeContains = exp.length() >= 3 && act.length() > 0 && act.contains(exp) && (exp.length() / (double) act.length()) >= 0.60d
                if (act == exp || safeContains) return true
                double sim = (double) Class.forName("com.qureon.locara.core.matcher.StringSimilarity").getMethod("compositeScore", String.class, String.class).invoke(null, act, exp)
                if (sim >= getConfiguredConfidenceThreshold(MIN_SEMANTIC_TEXT_THRESHOLD)) return true
            }
        } catch (Throwable healEx) { KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Healing failed for verifyElementText: " + healEx.getMessage()) }
        if (flow == FailureHandling.STOP_ON_FAILURE) throw t
        return false
    }

    private static Object healAndFailSafe(String objId, String label, FailureHandling flow, Throwable t, Object fallback, Closure<Boolean> healCall) {
        KeywordUtil.logInfo("[Locara by Qureon] 🛡️ Intercepted failure on ${label}('${objId}'). Activating in-flight self-healing...")
        try {
            def locara = getLocaraClass()
            if (locara != null && healCall.call(locara)) { KeywordUtil.logInfo("[Locara by Qureon] ✅ Healed '${objId}'!"); return fallback }
        } catch (Throwable healEx) { KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Healing failed: " + healEx.getMessage()) }
        if (flow == FailureHandling.STOP_ON_FAILURE) throw t
        return fallback
    }

    private static void installOptionalInterceptor(Map<String, List<IKeyword>> mobileCache, String key, String builtinClassName, Class<?> wrapperClass, Closure<IKeyword> wrapperFactory) {
        installInterceptor(mobileCache, key, { try { return (IKeyword) Class.forName(builtinClassName).getDeclaredConstructor().newInstance() } catch (Throwable ignored) { return null } }, wrapperClass, wrapperFactory)
    }

    private static void installInterceptor(Map<String, List<IKeyword>> mobileCache, String key, Closure<IKeyword> fallbackFactory, Class<?> wrapperClass, Closure<IKeyword> wrapperFactory) {
        List<IKeyword> existing = mobileCache.get(key)
        IKeyword original = (existing != null && !existing.isEmpty()) ? existing.get(0) : fallbackFactory.call()
        if (original != null && !wrapperClass.isInstance(original)) mobileCache.put(key, [wrapperFactory.call(original)])
    }

    static TestObject extractTestObject(Object[] params) { return (params != null && params.length > 0 && params[0] instanceof TestObject) ? (params[0] as TestObject) : null }
    static String extractObjId(TestObject to) { return to != null ? to.getObjectId() : "UnknownElement" }
    static int extractTimeout(Object[] params, int index, int defVal) { return (params != null && params.length > index && params[index] instanceof Number) ? ((Number) params[index]).intValue() : defVal }
    static FailureHandling extractFailureHandling(Object[] params, int index) { return (params != null && params.length > index && params[index] instanceof FailureHandling) ? (params[index] as FailureHandling) : RunConfiguration.getDefaultFailureHandling() }
    static void notifyManualScroll() { try { getLocaraClass()?.getMethod("notifyManualScroll")?.invoke(null) } catch (Throwable ignored) {} }
    static void recordSuccessfulTap() { try { getLocaraClass()?.getMethod("recordSuccessfulTap")?.invoke(null) } catch (Throwable ignored) {} }
    static AutoCloseable openActionWindow() { try { def r = getLocaraClass()?.getMethod("openActionWindow")?.invoke(null); return (r instanceof AutoCloseable) ? (AutoCloseable) r : null } catch (Throwable ignored) { return null } }
    static void closeActionWindow(AutoCloseable w) { if (w != null) { try { w.close() } catch (Throwable ignored) {} } }
}

class LocaraKeywordProxy implements IKeyword {
    protected final IKeyword originalKeyword
    protected final Object defaultValue
    private final boolean useActionWindow
    private final boolean countSuccessAsTap
    protected final ThreadLocal<Boolean> inFlight = ThreadLocal.withInitial { false }
    private final Closure<?> onFailure

    LocaraKeywordProxy(IKeyword originalKeyword, Object defaultValue, boolean useActionWindow, boolean countSuccessAsTap, Closure<?> onFailure = null) {
        this.originalKeyword = originalKeyword; this.defaultValue = defaultValue; this.useActionWindow = useActionWindow; this.countSuccessAsTap = countSuccessAsTap; this.onFailure = onFailure
    }

    @Override SupportLevel getSupportLevel(Object... params) { return originalKeyword?.getSupportLevel(params) }

    @Override Object execute(Object... params) {
        if (inFlight.get()) return originalKeyword != null ? originalKeyword.execute(params) : defaultValue
        AutoCloseable win = useActionWindow ? LocaraHook.openActionWindow() : null
        try {
            try {
                Object res = originalKeyword != null ? originalKeyword.execute(params) : defaultValue
                if (countSuccessAsTap) LocaraHook.recordSuccessfulTap()
                return res
            } catch (Throwable t) {
                inFlight.set(true)
                try { return onFailure.call(params, t) } finally { inFlight.remove() }
            }
        } finally { LocaraHook.closeActionWindow(win) }
    }
}

class LocaraProbeProxy extends LocaraKeywordProxy {
    private final boolean optInWaitPresent, throwOnNegativeStop
    private final String failSuffix
    private final Closure<Boolean> probeHeal

    LocaraProbeProxy(IKeyword orig, boolean optInWaitPresent, boolean throwOnNegativeStop, Closure<Boolean> probeHeal) { this(orig, optInWaitPresent, throwOnNegativeStop, "", probeHeal) }
    LocaraProbeProxy(IKeyword orig, boolean optInWaitPresent, boolean throwOnNegativeStop, String failSuffix, Closure<Boolean> probeHeal) {
        super(orig, false, false, false, null)
        this.optInWaitPresent = optInWaitPresent; this.throwOnNegativeStop = throwOnNegativeStop; this.failSuffix = failSuffix; this.probeHeal = probeHeal
    }

    @Override Object execute(Object... params) {
        if (inFlight.get()) return originalKeyword != null ? originalKeyword.execute(params) : false
        TestObject to = LocaraHook.extractTestObject(params); int timeout = LocaraHook.extractTimeout(params, 1, 10)
        FailureHandling flow = LocaraHook.extractFailureHandling(params, 2); String objId = LocaraHook.extractObjId(to)
        try {
            Object res = originalKeyword != null ? originalKeyword.execute(params) : false
            if (res instanceof Boolean && ((Boolean) res).booleanValue()) return true
        } catch (Throwable ignored) {}
        if ((optInWaitPresent && !LocaraHook.isWaitPresentHealingEnabled()) || LocaraHook.isNegativeTestCaseRunning()) {
            if (throwOnNegativeStop && flow == FailureHandling.STOP_ON_FAILURE) throw new StepFailedException("Element '${objId}' ${failSuffix}")
            return false
        }
        inFlight.set(true)
        try { if (probeHeal.call(to, timeout, objId)) return true }
        catch (Throwable healEx) { KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Probe healing failed: " + healEx.getMessage()) }
        finally { inFlight.remove() }
        if (throwOnNegativeStop && flow == FailureHandling.STOP_ON_FAILURE) throw new StepFailedException("Element '${objId}' ${failSuffix} after in-flight healing")
        return false
    }
}

class LocaraManualScrollInterceptor implements IKeyword {
    private final IKeyword originalKeyword
    LocaraManualScrollInterceptor(IKeyword originalKeyword) { this.originalKeyword = originalKeyword }
    @Override SupportLevel getSupportLevel(Object... params) { return originalKeyword?.getSupportLevel(params) }
    @Override Object execute(Object... params) {
        try { return originalKeyword != null ? originalKeyword.execute(params) : null } finally { LocaraHook.notifyManualScroll() }
    }
}
