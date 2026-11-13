package com.qureon.locara.katalon

import com.kms.katalon.core.annotation.Keyword
import com.kms.katalon.core.testobject.TestObject
import com.kms.katalon.core.util.KeywordUtil

/**
 * Native Katalon Studio Custom Keyword wrapper for Locara by Qureon (Ghost Mode).
 * Copy this file into your Katalon Studio project under:
 * Keywords/com/qureon/locara/katalon/LocaraKeywords.groovy
 *
 * Uses dynamic reflection so Katalon projects compile with ZERO errors even when
 * the optional locara-*.jar is not present in Drivers/.
 */
class LocaraKeywords {

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

    private static Object invokeLocara(String methodName, Class<?>[] paramTypes, Object[] args, Object fallback) {
        Class<?> clz = getLocaraClass()
        if (clz == null) {
            KeywordUtil.markWarning("[Locara by Qureon] locara JAR not found on Drivers classpath; Ghost Mode fallback for '${methodName}'.")
            return fallback
        }
        return clz.getMethod(methodName, paramTypes).invoke(null, args)
    }

    @Keyword(keywordObject = 'Locara')
    static boolean tap(TestObject testObject, String hint) {
        return invokeLocara("tap", [TestObject.class, String.class] as Class[], [testObject, hint] as Object[], false) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean tap(TestObject testObject, int timeoutSeconds, String hint) {
        return invokeLocara("tap", [TestObject.class, int.class, String.class] as Class[], [testObject, timeoutSeconds, hint] as Object[], false) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean tapByHint(String hint, String targetRsPath) {
        return invokeLocara("tapByHint", [String.class, String.class] as Class[], [hint, targetRsPath] as Object[], false) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean tapByHint(String hint, String targetRsPath, int timeoutSeconds) {
        return invokeLocara("tapByHint", [String.class, String.class, int.class] as Class[], [hint, targetRsPath, timeoutSeconds] as Object[], false) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean setText(TestObject testObject, String text, String hint) {
        return invokeLocara("setText", [TestObject.class, String.class, String.class] as Class[], [testObject, text, hint] as Object[], false) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean setText(TestObject testObject, String text, int timeoutSeconds, String hint) {
        return invokeLocara("setText", [TestObject.class, String.class, int.class, String.class] as Class[], [testObject, text, timeoutSeconds, hint] as Object[], false) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static String getText(TestObject testObject, String hint) {
        return invokeLocara("getText", [TestObject.class, String.class] as Class[], [testObject, hint] as Object[], "") as String
    }

    @Keyword(keywordObject = 'Locara')
    static String getText(TestObject testObject, int timeoutSeconds, String hint) {
        return invokeLocara("getText", [TestObject.class, int.class, String.class] as Class[], [testObject, timeoutSeconds, hint] as Object[], "") as String
    }

    @Keyword(keywordObject = 'Locara')
    static boolean clearText(TestObject testObject, String hint) {
        return invokeLocara("clearText", [TestObject.class, String.class] as Class[], [testObject, hint] as Object[], false) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean clearText(TestObject testObject, int timeoutSeconds, String hint) {
        return invokeLocara("clearText", [TestObject.class, int.class, String.class] as Class[], [testObject, timeoutSeconds, hint] as Object[], false) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean isElementPresent(TestObject testObject, String hint) {
        return invokeLocara("isElementPresent", [TestObject.class, String.class] as Class[], [testObject, hint] as Object[], false) as boolean
    }

    @Keyword(keywordObject = 'Locara')
    static boolean assertElementPresent(TestObject testObject, String hint) {
        return invokeLocara("assertElementPresent", [TestObject.class, String.class] as Class[], [testObject, hint] as Object[], false) as boolean
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
        Class<?> appTargetClz = Class.forName("com.qureon.locara.core.model.AppTarget")
        Object profile = appTargetClz.getMethod("fromString", String.class).invoke(null, profileName)
        clz.getMethod("setAppTarget", appTargetClz).invoke(null, profile)
    }
}
