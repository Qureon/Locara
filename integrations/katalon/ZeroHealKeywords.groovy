package com.zerohealing.katalon

import com.kms.katalon.core.annotation.Keyword
import com.kms.katalon.core.testobject.TestObject
import com.zerohealing.katalon.ZeroHeal

/**
 * Native Katalon Studio Custom Keyword wrapper for Zero-Healing.
 * Copy this file into your Katalon Studio project under:
 * Keywords/com/zerohealing/katalon/ZeroHealKeywords.groovy
 */
class ZeroHealKeywords {

    @Keyword(keywordObject = 'ZeroHeal')
    static boolean tap(TestObject testObject, String hint) {
        return ZeroHeal.tap(testObject, hint)
    }

    @Keyword(keywordObject = 'ZeroHeal')
    static boolean tap(TestObject testObject, int timeoutSeconds, String hint) {
        return ZeroHeal.tap(testObject, timeoutSeconds, hint)
    }

    @Keyword(keywordObject = 'ZeroHeal')
    static boolean tapByHint(String hint, String targetRsPath) {
        return ZeroHeal.tapByHint(hint, targetRsPath)
    }

    @Keyword(keywordObject = 'ZeroHeal')
    static boolean setText(TestObject testObject, String text, String hint) {
        return ZeroHeal.setText(testObject, text, hint)
    }
}
