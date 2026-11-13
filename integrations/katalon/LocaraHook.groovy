package core

import java.lang.reflect.Field
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.List
import java.util.Map

import com.kms.katalon.core.configuration.RunConfiguration
import com.kms.katalon.core.constants.CoreConstants
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
 * 🚀 LocaraHook — Native Engine Level Interceptor for Katalon Studio.
 *
 * Intercepts keywords at Katalon's core KeywordExecutor level.
 * Completely immune to @CompileStatic, CallSite caching, and Groovy dynamic dispatch limitations.
 * Provides 100% in-flight autonomous self-healing for Mobile.tap and Mobile.setText.
 */
class LocaraHook {

    private static volatile boolean installed = false

    private static Class<?> cachedLocaraClass = null
    private static volatile boolean checkedLocara = false

    static Class<?> getLocaraClass() {
        if (!checkedLocara) {
            try {
                cachedLocaraClass = Class.forName("com.qureon.locara.katalon.Locara")
            } catch (Throwable ignored) {
                cachedLocaraClass = null
            }
            checkedLocara = true
        }
        return cachedLocaraClass
    }

    static boolean isLocaraPresent() {
        return getLocaraClass() != null
    }

    static boolean isHealingEnabled() {
        if (!isLocaraPresent()) {
            return false
        }
        Boolean configured = readBooleanFlag(
                ["ENABLE_LOCARA", "ENABLE_QUREON", "ENABLE_ZERO_HEALING"],
                ["locara.enabled", "qureon.enabled", "healing.enabled"],
                ["LOCARA_ENABLED", "QUREON_ENABLED", "HEALING_ENABLED"]
        )
        return configured != null ? configured.booleanValue() : true
    }

    /**
     * Opt-in flag controlling whether conditional waitForElementPresent probes trigger healing.
     * Defaults to false so negative or optional popup probes do not create telemetry storms.
     */
    static boolean isWaitPresentHealingEnabled() {
        Boolean configured = readBooleanFlag(
                ["LOCARA_HOOK_HEAL_WAIT_PRESENT"],
                ["locara.hook.heal_wait_present"],
                ["LOCARA_HOOK_HEAL_WAIT_PRESENT"]
        )
        return configured != null && configured.booleanValue()
    }

    private static Boolean readBooleanFlag(List<String> gvNames, List<String> propKeys, List<String> envKeys) {
        // 1. Check GlobalVariable if defined in Katalon Execution Profile
        try {
            def gvClass = Class.forName("internal.GlobalVariable")
            for (String fName : gvNames) {
                try {
                    def field = gvClass.getField(fName)
                    if (field != null) {
                        Object val = field.get(null)
                        if (val != null) return Boolean.parseBoolean(val.toString())
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // 2. Check System Property
        for (String key : propKeys) {
            String sysProp = System.getProperty(key)
            if (sysProp != null) return Boolean.parseBoolean(sysProp)
        }

        // 3. Check Environment Variable
        for (String key : envKeys) {
            String env = System.getenv(key)
            if (env != null) return Boolean.parseBoolean(env)
        }

        // 4. Check local properties overrides (ignored in Git) or project properties
        try {
            String userDir = System.getProperty("user.dir")
            List<String> filenames = [
                "locara.local.properties", "qureon.local.properties",
                "locara.properties", "qureon.properties"
            ]
            for (String fn : filenames) {
                File f = new File(userDir, fn)
                if (f.exists()) {
                    Properties p = new Properties()
                    f.withInputStream { p.load(it) }
                    for (String key : propKeys) {
                        if (p.containsKey(key)) {
                            return Boolean.parseBoolean(p.getProperty(key))
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null
    }

    // Helper ramah untuk mendeteksi apakah skenario yang sedang jalan adalah Negative Test Case
    static boolean isNegativeTestCaseRunning() {
        try {
            def listenerClass = Class.forName("GlobalSetupListener")
            def field = listenerClass.getField("currentTestCaseId")
            String tcId = field.get(null)?.toString() ?: ""
            return tcId.contains("Negative") || tcId.contains("Negative Case") || tcId.contains("Negative Test Case") || tcId.contains("/TC-N") || tcId.contains("NTC -") || tcId.contains("/NTC")
        } catch (Throwable ignored) {
            return false
        }
    }

    static double getConfiguredConfidenceThreshold(double minimumFloor) {
        try {
            def locara = getLocaraClass()
            if (locara != null) {
                Object engine = locara.getMethod("getEngine").invoke(null)
                if (engine != null) {
                    Object cfg = engine.getClass().getMethod("getConfig").invoke(engine)
                    if (cfg != null) {
                        double configured = ((Number) cfg.getClass().getMethod("getConfidenceThreshold").invoke(cfg)).doubleValue()
                        return Math.max(minimumFloor, configured)
                    }
                }
            }
        } catch (Throwable ignored) {}
        return minimumFloor
    }

    static synchronized void install() {
        if (installed) return
        if (!isLocaraPresent()) return

        if (!isHealingEnabled()) {
            KeywordUtil.logInfo("[Locara by Qureon] Engine is DISABLED via configuration. Preserving 100% native Katalon execution.")
            return
        }

        try {
            KeywordExecutor executor = KeywordExecutor.getInstance(CoreConstants.PLATFORM_MOBILE)

            Field cacheField = KeywordExecutor.class.getDeclaredField("cachePlatforms")
            cacheField.setAccessible(true)
            @SuppressWarnings("unchecked")
            Map<String, Map<String, List<IKeyword>>> cachePlatforms =
                (Map<String, Map<String, List<IKeyword>>>) cacheField.get(executor)

            Map<String, List<IKeyword>> mobileCache = cachePlatforms.computeIfAbsent(
                CoreConstants.PLATFORM_MOBILE, { new HashMap<String, List<IKeyword>>() }
            )

            installInterceptor(mobileCache, "tap", { new TapKeyword() }, LocaraTapKeyword.class, { IKeyword orig -> new LocaraTapKeyword(orig) })
            installInterceptor(mobileCache, "setText", { new SetTextKeyword() }, LocaraSetTextKeyword.class, { IKeyword orig -> new LocaraSetTextKeyword(orig) })
            installInterceptor(mobileCache, "getText", { new GetTextKeyword() }, LocaraGetTextKeyword.class, { IKeyword orig -> new LocaraGetTextKeyword(orig) })
            installOptionalInterceptor(mobileCache, "verifyElementText", "com.kms.katalon.core.mobile.keyword.builtin.VerifyElementTextKeyword", LocaraVerifyElementTextKeyword.class, { IKeyword orig -> new LocaraVerifyElementTextKeyword(orig) })
            installOptionalInterceptor(mobileCache, "waitForElementPresent", "com.kms.katalon.core.mobile.keyword.builtin.WaitForElementPresentKeyword", LocaraWaitForElementPresentKeyword.class, { IKeyword orig -> new LocaraWaitForElementPresentKeyword(orig) })
            installOptionalInterceptor(mobileCache, "verifyElementPresent", "com.kms.katalon.core.mobile.keyword.builtin.VerifyElementPresentKeyword", LocaraVerifyElementPresentKeyword.class, { IKeyword orig -> new LocaraVerifyElementPresentKeyword(orig) })
            installOptionalInterceptor(mobileCache, "verifyElementVisible", "com.kms.katalon.core.mobile.keyword.builtin.VerifyElementVisibleKeyword", LocaraVerifyElementVisibleKeyword.class, { IKeyword orig -> new LocaraVerifyElementVisibleKeyword(orig) })

            installed = true
            KeywordUtil.logInfo("[Locara by Qureon] Native KeywordExecutor Hook v1.15.0 successfully installed (tap, setText, getText, verifyElementText, waitForElementPresent, verifyElementPresent/Visible intercepted)")
        } catch (Throwable t) {
            KeywordUtil.markWarning("[Locara by Qureon] Failed to install KeywordExecutor hook: " + t.getMessage())
        }
    }

    private static void installOptionalInterceptor(Map<String, List<IKeyword>> mobileCache, String key,
                                                   String builtinClassName, Class<?> wrapperClass,
                                                   groovy.lang.Closure<IKeyword> wrapperFactory) {
        installInterceptor(mobileCache, key, {
            try {
                Class<?> clz = Class.forName(builtinClassName)
                return (IKeyword) clz.getDeclaredConstructor().newInstance()
            } catch (Throwable ignored) {
                return null
            }
        }, wrapperClass, wrapperFactory)
    }

    private static void installInterceptor(Map<String, List<IKeyword>> mobileCache, String key,
                                           groovy.lang.Closure<IKeyword> fallbackFactory, Class<?> wrapperClass,
                                           groovy.lang.Closure<IKeyword> wrapperFactory) {
        IKeyword original = null
        List<IKeyword> existing = mobileCache.get(key)
        if (existing != null && !existing.isEmpty()) {
            original = existing.get(0)
        }
        if (original == null) {
            original = fallbackFactory.call()
        }
        if (original != null && !wrapperClass.isInstance(original)) {
            List<IKeyword> wrapped = new ArrayList<>()
            wrapped.add(wrapperFactory.call(original))
            mobileCache.put(key, wrapped)
        }
    }

    static TestObject extractTestObject(Object[] params) {
        return (params != null && params.length > 0 && params[0] instanceof TestObject) ? (params[0] as TestObject) : null
    }

    static int extractTimeout(Object[] params, int index, int defaultTimeout) {
        return (params != null && params.length > index && params[index] instanceof Number)
                ? ((Number) params[index]).intValue()
                : defaultTimeout
    }

    static FailureHandling extractFailureHandling(Object[] params, int index) {
        return (params != null && params.length > index && params[index] instanceof FailureHandling)
                ? (params[index] as FailureHandling)
                : RunConfiguration.getDefaultFailureHandling()
    }
}

class LocaraTapKeyword implements IKeyword {
    private final IKeyword originalKeyword
    private static final ThreadLocal<Boolean> IN_FLIGHT = ThreadLocal.withInitial { false }

    LocaraTapKeyword(IKeyword originalKeyword) {
        this.originalKeyword = originalKeyword
    }

    @Override
    SupportLevel getSupportLevel(Object... params) {
        return originalKeyword != null ? originalKeyword.getSupportLevel(params) : null
    }

    @Override
    Object execute(Object... params) {
        if (IN_FLIGHT.get()) {
            return originalKeyword != null ? originalKeyword.execute(params) : null
        }

        try {
            return originalKeyword != null ? originalKeyword.execute(params) : null
        } catch (Throwable t) {
            TestObject to = LocaraHook.extractTestObject(params)
            int timeout = LocaraHook.extractTimeout(params, 1, 10)
            FailureHandling flow = LocaraHook.extractFailureHandling(params, 2)

            String objId = to != null ? to.getObjectId() : "UnknownElement"
            KeywordUtil.logInfo("[Locara by Qureon] 🛡️ Intercepted failure on Mobile.tap('${objId}'). Activating in-flight self-healing...")

            IN_FLIGHT.set(true)
            try {
                def locara = LocaraHook.getLocaraClass()
                boolean healed = locara != null && (locara.healAndTap(to, timeout, objId) as boolean)
                if (healed) {
                    KeywordUtil.logInfo("[Locara by Qureon] ✅ In-flight self-healing succeeded for '${objId}'!")
                    return null
                }
            } catch (Throwable healEx) {
                KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Healing attempt failed: " + healEx.getMessage())
            } finally {
                IN_FLIGHT.remove()
            }

            if (flow == FailureHandling.STOP_ON_FAILURE) {
                throw t
            }
            return null
        }
    }
}

class LocaraSetTextKeyword implements IKeyword {
    private final IKeyword originalKeyword
    private static final ThreadLocal<Boolean> IN_FLIGHT = ThreadLocal.withInitial { false }

    LocaraSetTextKeyword(IKeyword originalKeyword) {
        this.originalKeyword = originalKeyword
    }

    @Override
    SupportLevel getSupportLevel(Object... params) {
        return originalKeyword != null ? originalKeyword.getSupportLevel(params) : null
    }

    @Override
    Object execute(Object... params) {
        if (IN_FLIGHT.get()) {
            return originalKeyword != null ? originalKeyword.execute(params) : null
        }

        try {
            return originalKeyword != null ? originalKeyword.execute(params) : null
        } catch (Throwable t) {
            TestObject to = LocaraHook.extractTestObject(params)
            String text = params != null && params.length > 1 && params[1] != null ? params[1].toString() : ""
            int timeout = LocaraHook.extractTimeout(params, 2, 10)
            FailureHandling flow = LocaraHook.extractFailureHandling(params, 3)

            String objId = to != null ? to.getObjectId() : "UnknownElement"
            KeywordUtil.logInfo("[Locara by Qureon] 🛡️ Intercepted failure on Mobile.setText('${objId}'). Activating in-flight self-healing...")

            IN_FLIGHT.set(true)
            try {
                def locara = LocaraHook.getLocaraClass()
                boolean healed = locara != null && (locara.healAndSetText(to, text, timeout, objId) as boolean)
                if (healed) {
                    KeywordUtil.logInfo("[Locara by Qureon] ✅ In-flight self-healing succeeded for '${objId}'!")
                    return null
                }
            } catch (Throwable healEx) {
                KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Healing attempt failed: " + healEx.getMessage())
            } finally {
                IN_FLIGHT.remove()
            }

            if (flow == FailureHandling.STOP_ON_FAILURE) {
                throw t
            }
            return null
        }
    }
}

class LocaraGetTextKeyword implements IKeyword {
    private final IKeyword originalKeyword
    private static final ThreadLocal<Boolean> IN_FLIGHT = ThreadLocal.withInitial { false }

    LocaraGetTextKeyword(IKeyword originalKeyword) {
        this.originalKeyword = originalKeyword
    }

    @Override
    SupportLevel getSupportLevel(Object... params) {
        return originalKeyword != null ? originalKeyword.getSupportLevel(params) : null
    }

    @Override
    Object execute(Object... params) {
        if (IN_FLIGHT.get()) {
            return originalKeyword != null ? originalKeyword.execute(params) : ""
        }

        try {
            return originalKeyword != null ? originalKeyword.execute(params) : ""
        } catch (Throwable t) {
            TestObject to = LocaraHook.extractTestObject(params)
            int timeout = LocaraHook.extractTimeout(params, 1, 10)
            FailureHandling flow = LocaraHook.extractFailureHandling(params, 2)

            String objId = to != null ? to.getObjectId() : "UnknownElement"
            KeywordUtil.logInfo("[Locara by Qureon] 🛡️ Intercepted failure on Mobile.getText('${objId}'). Activating in-flight self-healing...")

            IN_FLIGHT.set(true)
            try {
                def locara = LocaraHook.getLocaraClass()
                String healedText = locara != null ? (locara.healAndGetText(to, timeout, objId) as String) : null
                if (healedText != null) {
                    KeywordUtil.logInfo("[Locara by Qureon] ✅ In-flight self-healing succeeded for getText('${objId}') -> '${healedText}'!")
                    return healedText
                }
            } catch (Throwable healEx) {
                KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Healing attempt failed for getText: " + healEx.getMessage())
            } finally {
                IN_FLIGHT.remove()
            }

            if (flow == FailureHandling.STOP_ON_FAILURE) {
                throw t
            }
            return ""
        }
    }
}

class LocaraVerifyElementTextKeyword implements IKeyword {
    private final IKeyword originalKeyword
    private static final ThreadLocal<Boolean> IN_FLIGHT = ThreadLocal.withInitial { false }

    LocaraVerifyElementTextKeyword(IKeyword originalKeyword) {
        this.originalKeyword = originalKeyword
    }

    @Override
    SupportLevel getSupportLevel(Object... params) {
        return originalKeyword != null ? originalKeyword.getSupportLevel(params) : null
    }

    @Override
    Object execute(Object... params) {
        if (IN_FLIGHT.get()) {
            return originalKeyword != null ? originalKeyword.execute(params) : false
        }

        try {
            return originalKeyword != null ? originalKeyword.execute(params) : false
        } catch (Throwable t) {
            TestObject to = LocaraHook.extractTestObject(params)
            String expectedText = params != null && params.length > 1 && params[1] != null ? params[1].toString() : ""
            FailureHandling flow = LocaraHook.extractFailureHandling(params, 2)

            if (LocaraHook.isNegativeTestCaseRunning()) {
                if (flow == FailureHandling.STOP_ON_FAILURE) {
                    throw t
                }
                return false
            }

            String objId = to != null ? to.getObjectId() : "UnknownElement"
            KeywordUtil.logInfo("[Locara by Qureon] 🛡️ Intercepted failure on Mobile.verifyElementText('${objId}', '${expectedText}'). Activating in-flight self-healing...")

            IN_FLIGHT.set(true)
            try {
                def locara = LocaraHook.getLocaraClass()
                String actualText = locara != null ? (locara.healAndGetText(to, 10, objId) as String) : null
                if (actualText != null) {
                    String actClean = actualText.trim()
                    String expClean = expectedText.trim()
                    if (!expClean.isEmpty() && (actClean.equals(expClean) || actClean.contains(expClean))) {
                        KeywordUtil.logInfo("[Locara by Qureon] ✅ In-flight self-healing succeeded for verifyElementText('${objId}') -> Match!")
                        return true
                    }
                    double similarity = 0.0
                    try {
                        Class<?> simClass = Class.forName("com.qureon.locara.core.matcher.StringSimilarity")
                        def simMethod = simClass.getMethod("compositeScore", String.class, String.class)
                        similarity = (double) simMethod.invoke(null, actClean, expClean)
                    } catch (Throwable ignored) {}
                    double requiredThreshold = LocaraHook.getConfiguredConfidenceThreshold(0.80d)
                    if (!expClean.isEmpty() && similarity >= requiredThreshold) {
                        KeywordUtil.logInfo("[Locara by Qureon] ✅ In-flight self-healing succeeded for verifyElementText('${objId}'): Semantic match (${Math.round(similarity * 100)}%) between '${actualText}' and '${expectedText}'")
                        return true
                    }
                }
            } catch (Throwable healEx) {
                KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Healing attempt failed for verifyElementText: " + healEx.getMessage())
            } finally {
                IN_FLIGHT.remove()
            }

            if (flow == FailureHandling.STOP_ON_FAILURE) {
                throw t
            }
            return false
        }
    }
}

class LocaraWaitForElementPresentKeyword implements IKeyword {
    private final IKeyword originalKeyword
    private static final ThreadLocal<Boolean> IN_FLIGHT = ThreadLocal.withInitial { false }

    LocaraWaitForElementPresentKeyword(IKeyword originalKeyword) {
        this.originalKeyword = originalKeyword
    }

    @Override
    SupportLevel getSupportLevel(Object... params) {
        return originalKeyword != null ? originalKeyword.getSupportLevel(params) : null
    }

    @Override
    Object execute(Object... params) {
        if (IN_FLIGHT.get()) {
            return originalKeyword != null ? originalKeyword.execute(params) : false
        }

        TestObject to = LocaraHook.extractTestObject(params)
        int timeout = LocaraHook.extractTimeout(params, 1, 10)
        String objId = to != null ? to.getObjectId() : "UnknownElement"

        try {
            Object res = originalKeyword != null ? originalKeyword.execute(params) : false
            if (res instanceof Boolean && ((Boolean) res).booleanValue()) {
                return true
            }
        } catch (Throwable ignored) {}

        // Opt-in guard: waitForElementPresent is commonly used as an optional probe; avoid telemetry storms by default.
        if (!LocaraHook.isWaitPresentHealingEnabled() || LocaraHook.isNegativeTestCaseRunning()) {
            return false
        }

        // Original returned false or threw: Trigger In-Flight Probe Airbag!
        KeywordUtil.logInfo("[Locara by Qureon] 🛡️ Intercepted missing on Mobile.waitForElementPresent('${objId}'). Activating in-flight probe healing...")

        IN_FLIGHT.set(true)
        try {
            def locara = LocaraHook.getLocaraClass()
            boolean healed = locara != null && (locara.healAndVerifyPresent(to, timeout, objId) as boolean)
            if (healed) {
                KeywordUtil.logInfo("[Locara by Qureon] ✅ In-flight probe healing succeeded for waitForElementPresent('${objId}')!")
                return true
            }
        } catch (Throwable healEx) {
            KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Probe healing attempt failed: " + healEx.getMessage())
        } finally {
            IN_FLIGHT.remove()
        }

        return false
    }
}

class LocaraVerifyElementPresentKeyword implements IKeyword {
    private final IKeyword originalKeyword
    private static final ThreadLocal<Boolean> IN_FLIGHT = ThreadLocal.withInitial { false }

    LocaraVerifyElementPresentKeyword(IKeyword originalKeyword) {
        this.originalKeyword = originalKeyword
    }

    @Override
    SupportLevel getSupportLevel(Object... params) {
        return originalKeyword != null ? originalKeyword.getSupportLevel(params) : null
    }

    @Override
    Object execute(Object... params) {
        if (IN_FLIGHT.get()) {
            return originalKeyword != null ? originalKeyword.execute(params) : false
        }

        TestObject to = LocaraHook.extractTestObject(params)
        int timeout = LocaraHook.extractTimeout(params, 1, 10)
        FailureHandling flow = LocaraHook.extractFailureHandling(params, 2)
        String objId = to != null ? to.getObjectId() : "UnknownElement"

        try {
            Object res = originalKeyword != null ? originalKeyword.execute(params) : false
            if (res instanceof Boolean && ((Boolean) res).booleanValue()) {
                return true
            }
        } catch (Throwable ignored) {}

        if (LocaraHook.isNegativeTestCaseRunning()) {
            if (flow == FailureHandling.STOP_ON_FAILURE) {
                throw new com.kms.katalon.core.exception.StepFailedException("Element '" + objId + "' not present")
            }
            return false
        }

        KeywordUtil.logInfo("[Locara by Qureon] 🛡️ Intercepted failure on Mobile.verifyElementPresent('${objId}'). Activating in-flight probe healing...")

        IN_FLIGHT.set(true)
        try {
            def locara = LocaraHook.getLocaraClass()
            boolean healed = locara != null && (locara.healAndVerifyPresent(to, timeout, objId) as boolean)
            if (healed) {
                KeywordUtil.logInfo("[Locara by Qureon] ✅ In-flight probe healing succeeded for verifyElementPresent('${objId}')!")
                return true
            }
        } catch (Throwable healEx) {
            KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Probe healing attempt failed: " + healEx.getMessage())
        } finally {
            IN_FLIGHT.remove()
        }

        if (flow == FailureHandling.STOP_ON_FAILURE) {
            throw new com.kms.katalon.core.exception.StepFailedException("Element '" + objId + "' not present after in-flight probe healing")
        }
        return false
    }
}

class LocaraVerifyElementVisibleKeyword implements IKeyword {
    private final IKeyword originalKeyword
    private static final ThreadLocal<Boolean> IN_FLIGHT = ThreadLocal.withInitial { false }

    LocaraVerifyElementVisibleKeyword(IKeyword originalKeyword) {
        this.originalKeyword = originalKeyword
    }

    @Override
    SupportLevel getSupportLevel(Object... params) {
        return originalKeyword != null ? originalKeyword.getSupportLevel(params) : null
    }

    @Override
    Object execute(Object... params) {
        if (IN_FLIGHT.get()) {
            return originalKeyword != null ? originalKeyword.execute(params) : false
        }

        TestObject to = LocaraHook.extractTestObject(params)
        int timeout = LocaraHook.extractTimeout(params, 1, 10)
        FailureHandling flow = LocaraHook.extractFailureHandling(params, 2)
        String objId = to != null ? to.getObjectId() : "UnknownElement"

        try {
            Object res = originalKeyword != null ? originalKeyword.execute(params) : false
            if (res instanceof Boolean && ((Boolean) res).booleanValue()) {
                return true
            }
        } catch (Throwable ignored) {}

        if (LocaraHook.isNegativeTestCaseRunning()) {
            if (flow == FailureHandling.STOP_ON_FAILURE) {
                throw new com.kms.katalon.core.exception.StepFailedException("Element '" + objId + "' not visible")
            }
            return false
        }

        KeywordUtil.logInfo("[Locara by Qureon] 🛡️ Intercepted failure on Mobile.verifyElementVisible('${objId}'). Activating in-flight visibility healing...")

        IN_FLIGHT.set(true)
        try {
            def locara = LocaraHook.getLocaraClass()
            boolean healed = locara != null && (locara.healAndVerifyVisible(to, timeout, objId) as boolean)
            if (healed) {
                KeywordUtil.logInfo("[Locara by Qureon] ✅ In-flight visibility healing succeeded for verifyElementVisible('${objId}')!")
                return true
            }
        } catch (Throwable healEx) {
            KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Visibility healing attempt failed: " + healEx.getMessage())
        } finally {
            IN_FLIGHT.remove()
        }

        if (flow == FailureHandling.STOP_ON_FAILURE) {
            throw new com.kms.katalon.core.exception.StepFailedException("Element '" + objId + "' not visible after in-flight healing")
        }
        return false
    }
}

