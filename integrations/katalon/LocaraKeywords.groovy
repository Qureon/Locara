package com.qureon.locara.katalon

import com.kms.katalon.core.annotation.Keyword
import com.kms.katalon.core.testobject.TestObject
import com.qureon.locara.katalon.Locara

/**
 * Native Katalon Studio Custom Keyword wrapper for Locara by Qureon.
 * Copy this file into your Katalon Studio project under:
 * Keywords/com/zerohealing/katalon/LocaraKeywords.groovy
 */
class LocaraKeywords {

    @Keyword(keywordObject = 'Locara')
    static boolean tap(TestObject testObject, String hint) {
        return Locara.tap(testObject, hint)
    }

    @Keyword(keywordObject = 'Locara')
    static boolean tap(TestObject testObject, int timeoutSeconds, String hint) {
        return Locara.tap(testObject, timeoutSeconds, hint)
    }

    @Keyword(keywordObject = 'Locara')
    static boolean tapByHint(String hint, String targetRsPath) {
        return Locara.tapByHint(hint, targetRsPath)
    }

    @Keyword(keywordObject = 'Locara')
    static boolean setText(TestObject testObject, String text, String hint) {
        return Locara.setText(testObject, text, hint)
    }
}
