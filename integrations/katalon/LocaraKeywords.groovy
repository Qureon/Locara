package com.qureon.locara.katalon

import com.kms.katalon.core.annotation.Keyword
import com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords as Mobile
import com.kms.katalon.core.testobject.TestObject
import com.kms.katalon.core.util.KeywordUtil

/**
 * Native Katalon Studio Custom Keyword wrapper for Locara by Qureon (Ghost Mode).
 * Copy this file into your Katalon Studio project under:
 * Keywords/com/qureon/locara/katalon/LocaraKeywords.groovy
 *
 * Uses dynamic reflection so Katalon projects compile with ZERO errors even when
 * the optional locara-*.jar is not present in Drivers/, falling back seamlessly
 * to built-in Katalon Mobile keywords.
 */
class LocaraKeywords {

    private static final int DEFAULT_WAIT_TIMEOUT_SECONDS = 5
    private static volatile Class<?> cachedLocaraClass = null
    private static volatile boolean checkedLocara = false

    private static Class<?> getLocaraClass() {
        if (!checkedLocara) {
            synchronized (LocaraKeywords.class) {
                if (!checkedLocara) {
                    try {
                        cachedLocaraClass = Class.forName("com.qureon.locara.katalon.Locara")
                    } catch (Throwable ignored) {
                        cachedLocaraClass = null
                    }
                    checkedLocara = true
                }
            }
        }
        return cachedLocaraClass
    }

    private static Object invokeOrFallback(String methodName, Class<?>[] paramTypes, Object[] args, groovy.lang.Closure<?> ghostFallback) {
        Class<?> clz = getLocaraClass()
        if (clz == null) {
            KeywordUtil.logInfo("[Locara Ghost Mode] locara JAR not found on Drivers classpath; delegating '${methodName}' to native Mobile keyword.")
            return ghostFallback.call()
        }
        try {
            return clz.getMethod(methodName, paramTypes).invoke(null, args)
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause()
            if (cause instanceof Error) throw (Error) cause
            if (cause instanceof RuntimeException) throw (RuntimeException) cause
            throw new RuntimeException(cause != null ? cause : e)
        }
    }

    @Keyword(keywordObject = 'Locara')
    static boolean tap(TestObject testObject, String hint = "") {
        return tap(testObject, 10, hint)
    }

    @Keyword(keywordObject = 'Locara')
    static boolean tap(TestObject testObject, int timeoutSeconds, String hint = "") {
        return invokeOrFallback("tap", [Object.class, int.class, String.class] as Class[], [testObject, timeoutSeconds, hint] as Object[], {
            Mobile.tap(testObject, timeoutSeconds)
            return true
        }) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean tapByHint(String hint, String targetRsPath) {
        return tapByHint(hint, targetRsPath, 10)
    }

    @Keyword(keywordObject = 'Locara')
    static boolean tapByHint(String hint, String targetRsPath, int timeoutSeconds) {
        return invokeOrFallback("tapByHint", [String.class, int.class, String.class] as Class[], [hint, timeoutSeconds, targetRsPath] as Object[], {
            KeywordUtil.markWarning("[Locara Ghost Mode] tapByHint('${hint}') skipped because locara JAR is absent.")
            return false
        }) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean setText(TestObject testObject, String text, String hint = "") {
        return setText(testObject, text, 10, hint)
    }

    @Keyword(keywordObject = 'Locara')
    static boolean setText(TestObject testObject, String text, int timeoutSeconds, String hint = "") {
        return invokeOrFallback("setText", [Object.class, String.class, int.class, String.class] as Class[], [testObject, text, timeoutSeconds, hint] as Object[], {
            Mobile.setText(testObject, text, timeoutSeconds)
            return true
        }) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static String getText(TestObject testObject, String hint = "") {
        return getText(testObject, 10, hint)
    }

    @Keyword(keywordObject = 'Locara')
    static String getText(TestObject testObject, int timeoutSeconds, String hint = "") {
        return invokeOrFallback("getText", [Object.class, int.class, String.class] as Class[], [testObject, timeoutSeconds, hint] as Object[], {
            return Mobile.getText(testObject, timeoutSeconds)
        }) as String
    }

    @Keyword(keywordObject = 'Locara')
    static boolean clearText(TestObject testObject, String hint = "") {
        return clearText(testObject, 10, hint)
    }

    @Keyword(keywordObject = 'Locara')
    static boolean clearText(TestObject testObject, int timeoutSeconds, String hint = "") {
        return invokeOrFallback("clearText", [Object.class, int.class, String.class] as Class[], [testObject, timeoutSeconds, hint] as Object[], {
            Mobile.clearText(testObject, timeoutSeconds)
            return true
        }) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean isElementPresent(TestObject testObject, String hint = "") {
        return invokeOrFallback("isElementPresent", [Object.class, String.class] as Class[], [testObject, hint] as Object[], {
            return Mobile.waitForElementPresent(testObject, DEFAULT_WAIT_TIMEOUT_SECONDS)
        }) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean assertElementPresent(TestObject testObject, String hint = "") {
        return invokeOrFallback("assertElementPresent", [Object.class, String.class] as Class[], [testObject, hint] as Object[], {
            Mobile.verifyElementExist(testObject, 10)
            return true
        }) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean smartTap(TestObject testObject, int timeoutSeconds = 10) {
        return tap(testObject, timeoutSeconds, testObject != null ? testObject.getObjectId() : "")
    }

    @Keyword(keywordObject = 'Locara')
    static boolean smartSetText(TestObject testObject, String text, int timeoutSeconds = 10) {
        return setText(testObject, text, timeoutSeconds, testObject != null ? testObject.getObjectId() : "")
    }

    @Keyword(keywordObject = 'Locara')
    static void setTargetProfile(String profileName) {
        Class<?> clz = getLocaraClass()
        if (clz == null) {
            return
        }
        try {
            Class<?> appTargetClz = Class.forName("com.qureon.locara.core.model.AppTarget")
            Object profile = appTargetClz.getMethod("fromString", String.class).invoke(null, profileName)
            clz.getMethod("setAppTarget", appTargetClz).invoke(null, profile)
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause()
            if (cause instanceof Error) throw (Error) cause
            if (cause instanceof RuntimeException) throw (RuntimeException) cause
            throw new RuntimeException(cause != null ? cause : e)
        }
    }
}
