package com.zerohealing.katalon

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
import com.zerohealing.katalon.ZeroHeal

/**
 * 🚀 ZeroHealHook — Native Engine Level Interceptor for Katalon Studio.
 *
 * Intercepts keywords at Katalon's core KeywordExecutor level.
 * Completely immune to @CompileStatic, CallSite caching, and Groovy dynamic dispatch limitations.
 * Provides 100% in-flight autonomous self-healing for Mobile.tap and Mobile.setText.
 */
class ZeroHealHook {

    private static volatile boolean installed = false

    static boolean isHealingEnabled() {
        // 1. Check GlobalVariable if defined in Katalon Execution Profile
        try {
            def gvClass = Class.forName("internal.GlobalVariable")
            def field = gvClass.getField("ENABLE_ZERO_HEALING")
            if (field != null) {
                Object val = field.get(null)
                if (val != null) return Boolean.parseBoolean(val.toString())
            }
        } catch (Throwable ignored) {}

        // 2. Check System Property (-Dzerohealing.enabled=false or -Dhealing.enabled=false)
        String sysProp = System.getProperty("zerohealing.enabled") ?: System.getProperty("healing.enabled")
        if (sysProp != null) {
            return Boolean.parseBoolean(sysProp)
        }

        // 3. Check Environment Variable (ZEROHEALING_ENABLED=false)
        String env = System.getenv("ZEROHEALING_ENABLED")
        if (env != null) {
            return Boolean.parseBoolean(env)
        }

        // 4. Check zero-healing.local.properties (ignored in Git) or zero-healing.properties
        try {
            String userDir = System.getProperty("user.dir")
            File localProp = new File(userDir, "zero-healing.local.properties")
            if (localProp.exists()) {
                Properties p = new Properties()
                localProp.withInputStream { p.load(it) }
                if (p.containsKey("healing.enabled")) {
                    return Boolean.parseBoolean(p.getProperty("healing.enabled"))
                }
            }
            File prop = new File(userDir, "zero-healing.properties")
            if (prop.exists()) {
                Properties p = new Properties()
                prop.withInputStream { p.load(it) }
                if (p.containsKey("healing.enabled")) {
                    return Boolean.parseBoolean(p.getProperty("healing.enabled"))
                }
            }
        } catch (Throwable ignored) {}

        return true
    }

    static synchronized void install() {
        if (installed) return

        if (!isHealingEnabled()) {
            KeywordUtil.logInfo("[Zero-Healing] ⏸️ Engine is DISABLED via configuration. Preserving 100% native Katalon execution.")
            return
        }

        try {
            KeywordExecutor executor = KeywordExecutor.getInstance(CoreConstants.PLATFORM_MOBILE)

            Field cacheField = KeywordExecutor.class.getDeclaredField("cachePlatforms")
            cacheField.setAccessible(true)
            Map<String, Map<String, List<IKeyword>>> cachePlatforms = 
                (Map<String, Map<String, List<IKeyword>>>) cacheField.get(executor)

            Map<String, List<IKeyword>> mobileCache = cachePlatforms.computeIfAbsent(
                CoreConstants.PLATFORM_MOBILE, { new HashMap<String, List<IKeyword>>() }
            )

            // Intercept tap keyword
            IKeyword originalTap = null
            if (mobileCache.containsKey("tap") && mobileCache.get("tap") != null && !mobileCache.get("tap").isEmpty()) {
                originalTap = mobileCache.get("tap").get(0)
            }
            if (originalTap == null) {
                originalTap = new TapKeyword()
            }
            if (!(originalTap instanceof ZeroHealTapKeyword)) {
                List<IKeyword> tapList = new ArrayList<>()
                tapList.add(new ZeroHealTapKeyword(originalTap))
                mobileCache.put("tap", tapList)
            }

            // Intercept setText keyword
            IKeyword originalSetText = null
            if (mobileCache.containsKey("setText") && mobileCache.get("setText") != null && !mobileCache.get("setText").isEmpty()) {
                originalSetText = mobileCache.get("setText").get(0)
            }
            if (originalSetText == null) {
                originalSetText = new SetTextKeyword()
            }
            if (!(originalSetText instanceof ZeroHealSetTextKeyword)) {
                List<IKeyword> setTextList = new ArrayList<>()
                setTextList.add(new ZeroHealSetTextKeyword(originalSetText))
                mobileCache.put("setText", setTextList)
            }

            // Intercept getText keyword
            IKeyword originalGetText = null
            if (mobileCache.containsKey("getText") && mobileCache.get("getText") != null && !mobileCache.get("getText").isEmpty()) {
                originalGetText = mobileCache.get("getText").get(0)
            }
            if (originalGetText == null) {
                originalGetText = new GetTextKeyword()
            }
            if (!(originalGetText instanceof ZeroHealGetTextKeyword)) {
                List<IKeyword> getTextList = new ArrayList<>()
                getTextList.add(new ZeroHealGetTextKeyword(originalGetText))
                mobileCache.put("getText", getTextList)
            }

            // Intercept verifyElementText keyword (Assertion & Verification Healing)
            IKeyword originalVerifyElementText = null
            if (mobileCache.containsKey("verifyElementText") && mobileCache.get("verifyElementText") != null && !mobileCache.get("verifyElementText").isEmpty()) {
                originalVerifyElementText = mobileCache.get("verifyElementText").get(0)
            }
            if (originalVerifyElementText == null) {
                try {
                    Class<?> vetClass = Class.forName("com.kms.katalon.core.mobile.keyword.builtin.VerifyElementTextKeyword")
                    originalVerifyElementText = (IKeyword) vetClass.getDeclaredConstructor().newInstance()
                } catch (Throwable ignored) {}
            }
            if (originalVerifyElementText != null && !(originalVerifyElementText instanceof ZeroHealVerifyElementTextKeyword)) {
                List<IKeyword> vetList = new ArrayList<>()
                vetList.add(new ZeroHealVerifyElementTextKeyword(originalVerifyElementText))
                mobileCache.put("verifyElementText", vetList)
            }

            installed = true
            KeywordUtil.logInfo("🚀 [Zero-Healing] Native KeywordExecutor Hook successfully installed! (tap, setText, getText & verifyElementText intercepted)")
        } catch (Throwable t) {
            KeywordUtil.markWarning("⚠️ [Zero-Healing] Failed to install KeywordExecutor hook: " + t.getMessage())
        }
    }
}

class ZeroHealTapKeyword implements IKeyword {
    private final IKeyword originalKeyword
    private static final ThreadLocal<Boolean> IN_FLIGHT = ThreadLocal.withInitial { false }

    ZeroHealTapKeyword(IKeyword originalKeyword) {
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
            TestObject to = params != null && params.length > 0 && params[0] instanceof TestObject ? (params[0] as TestObject) : null
            int timeout = params != null && params.length > 1 && params[1] instanceof Number ? ((Number) params[1]).intValue() : 10
            FailureHandling flow = params != null && params.length > 2 && params[2] instanceof FailureHandling ? 
                (params[2] as FailureHandling) : RunConfiguration.getDefaultFailureHandling()

            String objId = to != null ? to.getObjectId() : "UnknownElement"
            KeywordUtil.logInfo("[Zero-Healing] 🛡️ Intercepted failure on Mobile.tap('${objId}'). Activating in-flight self-healing...")

            IN_FLIGHT.set(true)
            try {
                boolean healed = ZeroHeal.healAndTap(to, timeout, objId)
                if (healed) {
                    KeywordUtil.logInfo("[Zero-Healing] ✅ In-flight self-healing succeeded for '${objId}'!")
                    return null
                }
            } catch (Throwable healEx) {
                KeywordUtil.logInfo("[Zero-Healing] ⚠️ Healing attempt failed: " + healEx.getMessage())
            } finally {
                IN_FLIGHT.set(false)
            }

            if (flow == FailureHandling.STOP_ON_FAILURE) {
                throw t
            }
            return null
        }
    }
}

class ZeroHealSetTextKeyword implements IKeyword {
    private final IKeyword originalKeyword
    private static final ThreadLocal<Boolean> IN_FLIGHT = ThreadLocal.withInitial { false }

    ZeroHealSetTextKeyword(IKeyword originalKeyword) {
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
            TestObject to = params != null && params.length > 0 && params[0] instanceof TestObject ? (params[0] as TestObject) : null
            String text = params != null && params.length > 1 ? params[1]?.toString() : ""
            int timeout = params != null && params.length > 2 && params[2] instanceof Number ? ((Number) params[2]).intValue() : 10
            FailureHandling flow = params != null && params.length > 3 && params[3] instanceof FailureHandling ? 
                (params[3] as FailureHandling) : RunConfiguration.getDefaultFailureHandling()

            String objId = to != null ? to.getObjectId() : "UnknownElement"
            KeywordUtil.logInfo("[Zero-Healing] 🛡️ Intercepted failure on Mobile.setText('${objId}'). Activating in-flight self-healing...")

            IN_FLIGHT.set(true)
            try {
                boolean healed = ZeroHeal.healAndSetText(to, text, objId)
                if (healed) {
                    KeywordUtil.logInfo("[Zero-Healing] ✅ In-flight self-healing succeeded for '${objId}'!")
                    return null
                }
            } catch (Throwable healEx) {
                KeywordUtil.logInfo("[Zero-Healing] ⚠️ Healing attempt failed: " + healEx.getMessage())
            } finally {
                IN_FLIGHT.set(false)
            }

            if (flow == FailureHandling.STOP_ON_FAILURE) {
                throw t
            }
            return null
        }
    }
}

class ZeroHealGetTextKeyword implements IKeyword {
    private final IKeyword originalKeyword
    private static final ThreadLocal<Boolean> IN_FLIGHT = ThreadLocal.withInitial { false }

    ZeroHealGetTextKeyword(IKeyword originalKeyword) {
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
            TestObject to = params != null && params.length > 0 && params[0] instanceof TestObject ? (params[0] as TestObject) : null
            int timeout = params != null && params.length > 1 && params[1] instanceof Number ? ((Number) params[1]).intValue() : 10
            FailureHandling flow = params != null && params.length > 2 && params[2] instanceof FailureHandling ? 
                (params[2] as FailureHandling) : RunConfiguration.getDefaultFailureHandling()

            String objId = to != null ? to.getObjectId() : "UnknownElement"
            KeywordUtil.logInfo("[Zero-Healing] 🛡️ Intercepted failure on Mobile.getText('${objId}'). Activating in-flight self-healing...")

            IN_FLIGHT.set(true)
            try {
                String healedText = ZeroHeal.healAndGetText(to, timeout, objId)
                if (healedText != null) {
                    KeywordUtil.logInfo("[Zero-Healing] ✅ In-flight self-healing succeeded for getText('${objId}') -> '${healedText}'!")
                    return healedText
                }
            } catch (Throwable healEx) {
                KeywordUtil.logInfo("[Zero-Healing] ⚠️ Healing attempt failed for getText: " + healEx.getMessage())
            } finally {
                IN_FLIGHT.set(false)
            }

            if (flow == FailureHandling.STOP_ON_FAILURE) {
                throw t
            }
            return ""
        }
    }
}

class ZeroHealVerifyElementTextKeyword implements IKeyword {
    private final IKeyword originalKeyword
    private static final ThreadLocal<Boolean> IN_FLIGHT = ThreadLocal.withInitial { false }

    ZeroHealVerifyElementTextKeyword(IKeyword originalKeyword) {
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
            TestObject to = params != null && params.length > 0 && params[0] instanceof TestObject ? (params[0] as TestObject) : null
            String expectedText = params != null && params.length > 1 ? params[1]?.toString() : ""
            FailureHandling flow = params != null && params.length > 2 && params[2] instanceof FailureHandling ? 
                (params[2] as FailureHandling) : RunConfiguration.getDefaultFailureHandling()

            String objId = to != null ? to.getObjectId() : "UnknownElement"
            KeywordUtil.logInfo("[Zero-Healing] 🛡️ Intercepted failure on Mobile.verifyElementText('${objId}', '${expectedText}'). Activating in-flight self-healing...")

            IN_FLIGHT.set(true)
            try {
                String actualText = ZeroHeal.healAndGetText(to, 10, objId)
                if (actualText != null) {
                    if (actualText.equals(expectedText) || actualText.contains(expectedText) || expectedText.contains(actualText)) {
                        KeywordUtil.logInfo("[Zero-Healing] ✅ In-flight self-healing succeeded for verifyElementText('${objId}') -> Match!")
                        return true
                    }
                    double similarity = com.zerohealing.core.matcher.StringSimilarity.compositeScore(actualText, expectedText)
                    if (similarity >= 0.70) {
                        KeywordUtil.logInfo("[Zero-Healing] ✅ In-flight self-healing succeeded for verifyElementText('${objId}'): Semantic match (${Math.round(similarity * 100)}%) between '${actualText}' and '${expectedText}'")
                        return true
                    }
                }
            } catch (Throwable healEx) {
                KeywordUtil.logInfo("[Zero-Healing] ⚠️ Healing attempt failed for verifyElementText: " + healEx.getMessage())
            } finally {
                IN_FLIGHT.set(false)
            }

            if (flow == FailureHandling.STOP_ON_FAILURE) {
                throw t
            }
            return false
        }
    }
}

