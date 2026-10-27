import com.kms.katalon.core.annotation.AfterTestCase
import com.kms.katalon.core.context.TestCaseContext
import com.kms.katalon.core.mobile.keyword.internal.MobileDriverFactory
import com.zerohealing.core.ZeroHealingEngine
import com.zerohealing.core.model.HealContext
import com.zerohealing.core.model.HealResult

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Zero-Code-Change Katalon Studio Test Listener.
 * Copy this file into your Katalon Studio project under:
 * Test Listeners/ZeroHealTestListener.groovy
 *
 * Automatically intercepts any failed test case caused by missing or changed locators,
 * analyzes the live Appium page source, and synthesizes permanent .rs Object Repositories!
 */
class ZeroHealTestListener {

    private static final ZeroHealingEngine ENGINE = new ZeroHealingEngine()
    private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("Object Repository/([^\'\"\\s]+)")

    @AfterTestCase
    def onTestCaseFailure(TestCaseContext testCaseContext) {
        if (testCaseContext.getTestCaseStatus() != 'FAILED') {
            return
        }

        String errorMsg = testCaseContext.getMessage()
        if (errorMsg == null) return

        // Check if failure is due to missing element / locator mismatch
        boolean isElementFailure = errorMsg.contains("NoSuchElementException") ||
                errorMsg.contains("Unable to find the object") ||
                errorMsg.contains("Cannot find element") ||
                errorMsg.contains("Object not found")

        if (!isElementFailure) {
            return
        }

        println("==============================================================")
        println("  🛡️  [Zero-Healing Listener] Intercepted Test Case Failure!  ")
        println("  TestCase: " + testCaseContext.getTestCaseId())
        println("==============================================================")

        try {
            def driver = MobileDriverFactory.getMobileDriver()
            if (driver == null) {
                println("[Zero-Healing] No active MobileDriver found. Skipping listener recovery.")
                return
            }

            String pageSource = driver.getPageSource()
            if (pageSource == null || pageSource.isEmpty()) {
                return
            }

            // Safety guard: Do not overwrite object repositories if on a crash/system error screen
            String lowerSource = pageSource.toLowerCase()
            if (lowerSource.contains("has stopped") ||
                lowerSource.contains("system error") ||
                lowerSource.contains("sesi anda telah berakhir") ||
                lowerSource.contains("koneksi terputus")) {
                println("⚠️ [Zero-Healing] System error / crash dialog detected on active screen. Skipping Object Repository overwrite to prevent locator corruption.")
                return
            }

            // Extract failed object repository path from error message
            String failedObjectPath = ""
            Matcher matcher = OBJECT_ID_PATTERN.matcher(errorMsg)
            if (matcher.find()) {
                failedObjectPath = "Object Repository/" + matcher.group(1)
            }

            String objectName = failedObjectPath.isEmpty() ? "UnknownElement" :
                    failedObjectPath.substring(failedObjectPath.lastIndexOf('/') + 1).replace(".rs", "")

            println("[Zero-Healing] Diagnosing failed element: " + objectName + " (" + failedObjectPath + ")")

            HealContext context = HealContext.builder()
                    .targetHint(objectName)
                    .screenName(testCaseContext.getTestCaseId().replace('/', '_'))
                    .targetRsPath(failedObjectPath.isEmpty() ? "Object Repository/Healed/" + objectName + ".rs" : failedObjectPath)
                    .build()

            HealResult result = ENGINE.heal(pageSource, context)
            if (result.isSuccess() && result.getCandidate() != null) {
                println("🎉 [Zero-Healing Success] Automatically healed element for next run!")
                println("   Optimal XPath : " + result.getCandidate().getSuggestedXPath())
                println("   Confidence    : " + String.format(Locale.US, "%.1f%%", result.getCandidate().getConfidenceScore() * 100.0))
                if (result.getGeneratedRsFile() != null) {
                    println("   Updated .rs   : " + result.getGeneratedRsFile().getAbsolutePath())
                }
                println("💡 Tip: Re-run your test case now. The healed Object Repository has been updated!")
            } else {
                println("⚠️ [Zero-Healing] Could not find high-confidence candidate: " + result.getMessage())
            }
        } catch (Throwable t) {
            println("[Zero-Healing] Listener diagnostic encounter: " + t.getMessage())
        }
    }
}
