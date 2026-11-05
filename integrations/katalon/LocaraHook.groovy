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
        // 1. Check GlobalVariable if defined in Katalon Execution Profile
        try {
            def gvClass = Class.forName("internal.GlobalVariable")
            def field = null
            for (String fName : ["ENABLE_LOCARA", "ENABLE_QUREON", "ENABLE_ZERO_HEALING"]) {
                try {
                    field = gvClass.getField(fName)
                    if (field != null) break
                } catch (Throwable ignored) {}
            }
            if (field != null) {
                Object val = field.get(null)
                if (val != null) return Boolean.parseBoolean(val.toString())
            }
        } catch (Throwable ignored) {}

        // 2. Check System Property
        for (String key : ["locara.enabled", "qureon.enabled", "healing.enabled"]) {
            String sysProp = System.getProperty(key)
            if (sysProp != null) return Boolean.parseBoolean(sysProp)
        }

        // 3. Check Environment Variable
        for (String key : ["LOCARA_ENABLED", "QUREON_ENABLED", "HEALING_ENABLED"]) {
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
                    if (p.containsKey("locara.enabled")) {
                        return Boolean.parseBoolean(p.getProperty("locara.enabled"))
                    }
                    if (p.containsKey("qureon.enabled")) {
                        return Boolean.parseBoolean(p.getProperty("qureon.enabled"))
                    }
                    if (p.containsKey("healing.enabled")) {
                        return Boolean.parseBoolean(p.getProperty("healing.enabled"))
                    }
                }
            }
        } catch (Throwable ignored) {}

        return true
    }

    // Helper ramah untuk mendeteksi apakah skenario yang sedang jalan adalah Negative Test Case
    static boolean isNegativeTestCaseRunning() {
        try {
            def listenerClass = Class.forName("GlobalSetupListener")
            def field = listenerClass.getField("currentTestCaseId")
            String tcId = field.get(null)?.toString() ?: ""
            return tcId.contains("Negative") || tcId.contains("Negative Case") || tcId.contains("Negative Test Case") || tcId.contains("/TC-N")
        } catch (Throwable ignored) {
            return false
        }
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
            if (!(originalTap instanceof LocaraTapKeyword)) {
                List<IKeyword> tapList = new ArrayList<>()
                tapList.add(new LocaraTapKeyword(originalTap))
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
            if (!(originalSetText instanceof LocaraSetTextKeyword)) {
                List<IKeyword> setTextList = new ArrayList<>()
                setTextList.add(new LocaraSetTextKeyword(originalSetText))
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
            if (!(originalGetText instanceof LocaraGetTextKeyword)) {
                List<IKeyword> getTextList = new ArrayList<>()
                getTextList.add(new LocaraGetTextKeyword(originalGetText))
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
            if (originalVerifyElementText != null && !(originalVerifyElementText instanceof LocaraVerifyElementTextKeyword)) {
                List<IKeyword> vetList = new ArrayList<>()
                vetList.add(new LocaraVerifyElementTextKeyword(originalVerifyElementText))
                mobileCache.put("verifyElementText", vetList)
            }

            // Intercept waitForElementPresent keyword (Probing & Presence Healing)
            IKeyword originalWaitForElementPresent = null
            if (mobileCache.containsKey("waitForElementPresent") && mobileCache.get("waitForElementPresent") != null && !mobileCache.get("waitForElementPresent").isEmpty()) {
                originalWaitForElementPresent = mobileCache.get("waitForElementPresent").get(0)
            }
            if (originalWaitForElementPresent == null) {
                try {
                    Class<?> wfClass = Class.forName("com.kms.katalon.core.mobile.keyword.builtin.WaitForElementPresentKeyword")
                    originalWaitForElementPresent = (IKeyword) wfClass.getDeclaredConstructor().newInstance()
                } catch (Throwable ignored) {}
            }
            if (originalWaitForElementPresent != null && !(originalWaitForElementPresent instanceof LocaraWaitForElementPresentKeyword)) {
                List<IKeyword> wfList = new ArrayList<>()
                wfList.add(new LocaraWaitForElementPresentKeyword(originalWaitForElementPresent))
                mobileCache.put("waitForElementPresent", wfList)
            }

            // Intercept verifyElementPresent keyword
            IKeyword originalVerifyElementPresent = null
            if (mobileCache.containsKey("verifyElementPresent") && mobileCache.get("verifyElementPresent") != null && !mobileCache.get("verifyElementPresent").isEmpty()) {
                originalVerifyElementPresent = mobileCache.get("verifyElementPresent").get(0)
            }
            if (originalVerifyElementPresent == null) {
                try {
                    Class<?> vepClass = Class.forName("com.kms.katalon.core.mobile.keyword.builtin.VerifyElementPresentKeyword")
                    originalVerifyElementPresent = (IKeyword) vepClass.getDeclaredConstructor().newInstance()
                } catch (Throwable ignored) {}
            }
            if (originalVerifyElementPresent != null && !(originalVerifyElementPresent instanceof LocaraVerifyElementPresentKeyword)) {
                List<IKeyword> vepList = new ArrayList<>()
                vepList.add(new LocaraVerifyElementPresentKeyword(originalVerifyElementPresent))
                mobileCache.put("verifyElementPresent", vepList)
            }

            // Intercept verifyElementVisible keyword
            IKeyword originalVerifyElementVisible = null
            if (mobileCache.containsKey("verifyElementVisible") && mobileCache.get("verifyElementVisible") != null && !mobileCache.get("verifyElementVisible").isEmpty()) {
                originalVerifyElementVisible = mobileCache.get("verifyElementVisible").get(0)
            }
            if (originalVerifyElementVisible == null) {
                try {
                    Class<?> vevClass = Class.forName("com.kms.katalon.core.mobile.keyword.builtin.VerifyElementVisibleKeyword")
                    originalVerifyElementVisible = (IKeyword) vevClass.getDeclaredConstructor().newInstance()
                } catch (Throwable ignored) {}
            }
            if (originalVerifyElementVisible != null && !(originalVerifyElementVisible instanceof LocaraVerifyElementVisibleKeyword)) {
                List<IKeyword> vevList = new ArrayList<>()
                vevList.add(new LocaraVerifyElementVisibleKeyword(originalVerifyElementVisible))
                mobileCache.put("verifyElementVisible", vevList)
            }

            installed = true
            KeywordUtil.logInfo("[Locara by Qureon] Native KeywordExecutor Hook v1.4.0 successfully installed (tap, setText, getText, verifyElementText, waitForElementPresent, verifyElementPresent/Visible intercepted)")
        } catch (Throwable t) {
            KeywordUtil.markWarning("[Locara by Qureon] Failed to install KeywordExecutor hook: " + t.getMessage())
        }
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
            TestObject to = params != null && params.length > 0 && params[0] instanceof TestObject ? (params[0] as TestObject) : null
            int timeout = params != null && params.length > 1 && params[1] instanceof Number ? ((Number) params[1]).intValue() : 10
            FailureHandling flow = params != null && params.length > 2 && params[2] instanceof FailureHandling ? 
                (params[2] as FailureHandling) : RunConfiguration.getDefaultFailureHandling()

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
                IN_FLIGHT.set(false)
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
            TestObject to = params != null && params.length > 0 && params[0] instanceof TestObject ? (params[0] as TestObject) : null
            String text = params != null && params.length > 1 ? params[1]?.toString() : ""
            int timeout = params != null && params.length > 2 && params[2] instanceof Number ? ((Number) params[2]).intValue() : 10
            FailureHandling flow = params != null && params.length > 3 && params[3] instanceof FailureHandling ? 
                (params[3] as FailureHandling) : RunConfiguration.getDefaultFailureHandling()

            String objId = to != null ? to.getObjectId() : "UnknownElement"
            KeywordUtil.logInfo("[Locara by Qureon] 🛡️ Intercepted failure on Mobile.setText('${objId}'). Activating in-flight self-healing...")

            IN_FLIGHT.set(true)
            try {
                def locara = LocaraHook.getLocaraClass()
                boolean healed = locara != null && (locara.healAndSetText(to, text, objId) as boolean)
                if (healed) {
                    KeywordUtil.logInfo("[Locara by Qureon] ✅ In-flight self-healing succeeded for '${objId}'!")
                    return null
                }
            } catch (Throwable healEx) {
                KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Healing attempt failed: " + healEx.getMessage())
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
            TestObject to = params != null && params.length > 0 && params[0] instanceof TestObject ? (params[0] as TestObject) : null
            int timeout = params != null && params.length > 1 && params[1] instanceof Number ? ((Number) params[1]).intValue() : 10
            FailureHandling flow = params != null && params.length > 2 && params[2] instanceof FailureHandling ? 
                (params[2] as FailureHandling) : RunConfiguration.getDefaultFailureHandling()

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
                IN_FLIGHT.set(false)
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
            TestObject to = params != null && params.length > 0 && params[0] instanceof TestObject ? (params[0] as TestObject) : null
            String expectedText = params != null && params.length > 1 ? params[1]?.toString() : ""
            FailureHandling flow = params != null && params.length > 2 && params[2] instanceof FailureHandling ? 
                (params[2] as FailureHandling) : RunConfiguration.getDefaultFailureHandling()

            String objId = to != null ? to.getObjectId() : "UnknownElement"
            KeywordUtil.logInfo("[Locara by Qureon] 🛡️ Intercepted failure on Mobile.verifyElementText('${objId}', '${expectedText}'). Activating in-flight self-healing...")

            IN_FLIGHT.set(true)
            try {
                def locara = LocaraHook.getLocaraClass()
                String actualText = locara != null ? (locara.healAndGetText(to, 10, objId) as String) : null
                if (actualText != null) {
                    String actClean = actualText.trim()
                    String expClean = expectedText != null ? expectedText.trim() : ""
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
                    if (similarity >= 0.80) {
                        KeywordUtil.logInfo("[Locara by Qureon] ✅ In-flight self-healing succeeded for verifyElementText('${objId}'): Semantic match (${Math.round(similarity * 100)}%) between '${actualText}' and '${expectedText}'")
                        return true
                    }
                }
            } catch (Throwable healEx) {
                KeywordUtil.logInfo("[Locara by Qureon] ⚠️ Healing attempt failed for verifyElementText: " + healEx.getMessage())
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

        TestObject to = params != null && params.length > 0 && params[0] instanceof TestObject ? (params[0] as TestObject) : null
        int timeout = params != null && params.length > 1 && params[1] instanceof Number ? ((Number) params[1]).intValue() : 10
        FailureHandling flow = params != null && params.length > 2 && params[2] instanceof FailureHandling ? 
            (params[2] as FailureHandling) : RunConfiguration.getDefaultFailureHandling()

        String objId = to != null ? to.getObjectId() : "UnknownElement"

        try {
            Object res = originalKeyword != null ? originalKeyword.execute(params) : false
            if (res instanceof Boolean && ((Boolean) res).booleanValue()) {
                return true
            }
        } catch (Throwable ignored) {}

        // 🛡️ Jika sedang menjalankan Negative Test Case, jangan paksakan healing agar assertion negatif tetap akurat
        if (LocaraHook.isNegativeTestCaseRunning()) {
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
            IN_FLIGHT.set(false)
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

        TestObject to = params != null && params.length > 0 && params[0] instanceof TestObject ? (params[0] as TestObject) : null
        int timeout = params != null && params.length > 1 && params[1] instanceof Number ? ((Number) params[1]).intValue() : 10
        FailureHandling flow = params != null && params.length > 2 && params[2] instanceof FailureHandling ? 
            (params[2] as FailureHandling) : RunConfiguration.getDefaultFailureHandling()

        String objId = to != null ? to.getObjectId() : "UnknownElement"

        try {
            Object res = originalKeyword != null ? originalKeyword.execute(params) : false
            if (res instanceof Boolean && ((Boolean) res).booleanValue()) {
                return true
            }
        } catch (Throwable t) {}

        // 🛡️ Jika sedang menjalankan Negative Test Case, jangan paksakan healing
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
            IN_FLIGHT.set(false)
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

        TestObject to = params != null && params.length > 0 && params[0] instanceof TestObject ? (params[0] as TestObject) : null
        int timeout = params != null && params.length > 1 && params[1] instanceof Number ? ((Number) params[1]).intValue() : 10
        FailureHandling flow = params != null && params.length > 2 && params[2] instanceof FailureHandling ? 
            (params[2] as FailureHandling) : RunConfiguration.getDefaultFailureHandling()

        String objId = to != null ? to.getObjectId() : "UnknownElement"

        try {
            Object res = originalKeyword != null ? originalKeyword.execute(params) : false
            if (res instanceof Boolean && ((Boolean) res).booleanValue()) {
                return true
            }
        } catch (Throwable t) {}

        // 🛡️ Jika sedang menjalankan Negative Test Case, jangan paksakan healing
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
            IN_FLIGHT.set(false)
        }

        if (flow == FailureHandling.STOP_ON_FAILURE) {
            throw new com.kms.katalon.core.exception.StepFailedException("Element '" + objId + "' not visible after in-flight healing")
        }
        return false
    }
}

