package com.qureon.locara.katalon;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.driver.AppiumDriverActionBridge;
import com.qureon.locara.core.generator.DynamicXPathGenerator;
import com.qureon.locara.core.guard.DangerKeywordGuard;
import com.qureon.locara.core.guard.OverlayAirbag;
import com.qureon.locara.core.ledger.HealingAuditLedger;
import com.qureon.locara.core.logging.LocaraLogger;
import com.qureon.locara.core.matcher.LocaraIntentMatcher;
import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import com.qureon.locara.core.model.UIElementNode;

import java.util.List;
import java.util.Locale;

/**
 * Coordinates Katalon self-healing execution across OverlayAirbag, Heuristic/Intent matching,
 * ScrollRecoveryEngine, KeyboardGuard, TestObject patching, and WebDriver action execution.
 */
final class KatalonHealingCoordinator {

    static final String LOG_PREFIX = "[Locara by Qureon]";
    static final double MIN_SEMANTIC_INTENT_FLOOR = 0.85;

    private final LocaraEngine engine;
    private final KeyboardGuard keyboardGuard;
    private final OverlayAirbag overlayAirbag;
    private final DynamicXPathGenerator xpathGenerator;

    KatalonHealingCoordinator(LocaraEngine engine,
                              KeyboardGuard keyboardGuard,
                              OverlayAirbag overlayAirbag,
                              DynamicXPathGenerator xpathGenerator) {
        this.engine = engine;
        this.keyboardGuard = keyboardGuard;
        this.overlayAirbag = overlayAirbag;
        this.xpathGenerator = xpathGenerator;
    }

    boolean executeHealing(Object testObject,
                           String hint,
                           String action,
                           String value,
                           int timeoutSeconds,
                           String explicitRsPath,
                           ScrollRecoveryEngine scrollEngine) {
        Object driver = requireDriver();
        String pageSource = requirePageSource(driver);

        HealContext context = KatalonObjectExtractor.buildHealContext(testObject, hint, action, value, explicitRsPath);
        List<UIElementNode> nodes = safeParsePageSource(pageSource);
        HealResult result = engine.heal(nodes, pageSource, context);

        if (!result.isSuccess() && nodes != null && overlayAirbag.dismissBlockingOverlay(driver, nodes)) {
            pageSource = KatalonDriverBridge.getPageSource(driver);
            if (pageSource != null && !pageSource.isEmpty()) {
                nodes = safeParsePageSource(pageSource);
                result = engine.heal(nodes, pageSource, context);
            }
        }

        if (!result.isSuccess() && nodes != null) {
            result = scrollEngine.recoverWithScroll(
                    engine.getConfig(), engine, context, nodes, pageSource, result,
                    driver, () -> KatalonDriverBridge.getPageSource(driver), LOG_PREFIX
            );
        }

        if (!result.isSuccess() || result.getCandidate() == null) {
            throw new RuntimeException("[Locara by Qureon Failed] " + result.getMessage());
        }

        String healedXPath = patchAndLog(testObject, hint, result);
        keyboardGuard.guardElement(driver, result.getCandidate().getNode());

        try {
            Object element = AppiumDriverActionBridge.findElementWithTimeout(driver, healedXPath, timeoutSeconds);
            boolean executed = AppiumDriverActionBridge.executeElementAction(element, action, value);
            if (executed && ("TAP".equalsIgnoreCase(action) || "CLICK".equalsIgnoreCase(action))) {
                ManualScrollGuard.markActionTapSucceeded();
            }
            return executed;
        } catch (Exception e) {
            throw new RuntimeException(LOG_PREFIX + " Failed to execute retry action on healed element: " + e.getMessage(), e);
        }
    }

    String healAndGetText(Object testObject, int timeoutSeconds, String hint) {
        Object driver = requireDriver();
        String pageSource = requirePageSource(driver);

        HealContext context = KatalonObjectExtractor.buildHealContext(
                testObject, hint, "GET_TEXT", "", KatalonObjectExtractor.extractObjectId(testObject)
        );
        List<UIElementNode> nodes = safeParsePageSource(pageSource);
        HealResult result = engine.heal(nodes, pageSource, context);

        if (!result.isSuccess() && nodes != null && overlayAirbag.dismissBlockingOverlay(driver, nodes)) {
            pageSource = KatalonDriverBridge.getPageSource(driver);
            if (pageSource != null) {
                result = engine.heal(safeParsePageSource(pageSource), pageSource, context);
            }
        }

        if (!result.isSuccess() || result.getCandidate() == null) {
            throw new RuntimeException("[Locara by Qureon Failed] " + result.getMessage());
        }

        String healedXPath = patchAndLog(testObject, hint, result);
        try {
            Object element = AppiumDriverActionBridge.findElementWithTimeout(driver, healedXPath, timeoutSeconds);
            return AppiumDriverActionBridge.readElementText(element);
        } catch (Exception e) {
            throw new RuntimeException(LOG_PREFIX + " Failed to read text from healed element: " + e.getMessage(), e);
        }
    }

    String resolvePresentXPathAndPatch(Object driver, Object testObject, String hint) {
        if (driver == null) {
            return null;
        }
        try {
            String pageSource = KatalonDriverBridge.getPageSource(driver);
            if (pageSource == null || pageSource.isEmpty()) {
                return null;
            }

            HealContext context = KatalonObjectExtractor.buildHealContext(
                    testObject, hint, "VERIFY_PRESENT", "", KatalonObjectExtractor.extractObjectId(testObject)
            );
            List<UIElementNode> nodes = safeParsePageSource(pageSource);
            HealResult result = engine.heal(nodes, pageSource, context);

            if (!result.isSuccess() && nodes != null && !nodes.isEmpty()) {
                result = trySemanticIntentFallback(context, nodes);
            }
            if (!result.isSuccess() || result.getCandidate() == null) {
                return null;
            }

            String healedXPath = patchAndLog(testObject, hint, result);
            return healedXPath;
        } catch (Throwable t) {
            LocaraLogger.warn(LOG_PREFIX, "Presence probe healing encountered: " + t.getMessage());
            return null;
        }
    }

    private HealResult trySemanticIntentFallback(HealContext context, List<UIElementNode> nodes) {
        String effectiveHint = context.getTargetHint();
        UIElementNode bestNode = null;
        double bestScore = 0.0;
        double requiredFloor = Math.max(MIN_SEMANTIC_INTENT_FLOOR, engine.getConfig().getMinConfidenceThreshold());

        for (UIElementNode node : nodes) {
            if (!node.isDisplayed() || DangerKeywordGuard.isPolarityViolation(effectiveHint, node)) {
                continue;
            }
            if (node.hasText()) {
                double score = LocaraIntentMatcher.calculateIntentScore(node.getText(), effectiveHint);
                if (score > bestScore && score >= requiredFloor) {
                    bestScore = score;
                    bestNode = node;
                }
            }
        }
        if (bestNode == null) {
            return new HealResult(false, null, null, "No semantic intent candidate exceeded threshold.");
        }
        String generatedXPath = xpathGenerator.generateOptimalXPath(bestNode, nodes, "VERIFY_PRESENT");
        HealCandidate candidate = new HealCandidate(bestNode, generatedXPath, bestScore, "SEMANTIC_INTENT_MATCH");
        new HealingAuditLedger().recordEvent(context, candidate, null, "SUCCESS", engine.getConfig());
        return new HealResult(true, candidate, null,
                "Healed via Semantic Intent Matching (" + String.format(Locale.US, "%.1f%%", bestScore * 100.0) + ")");
    }

    private String patchAndLog(Object testObject, String hint, HealResult result) {
        String healedXPath = result.getCandidate().getSuggestedXPath();
        LocaraLogger.info(LOG_PREFIX, "🌟 HEALED: " + hint + " -> XPath: " + healedXPath + " (Score: " +
                String.format(Locale.US, "%.1f%%", result.getCandidate().getConfidenceScore() * 100.0) + ")");
        if (result.getGeneratedRsFile() != null) {
            LocaraLogger.info(LOG_PREFIX, "📝 Object Repository (.rs) written to: "
                    + result.getGeneratedRsFile().getAbsolutePath());
        }
        if (testObject != null) {
            KatalonDriverBridge.patchTestObject(testObject, result.getCandidate().getNode(), healedXPath);
        }
        return healedXPath;
    }

    private List<UIElementNode> safeParsePageSource(String pageSource) {
        try {
            return engine.parsePageSource(pageSource);
        } catch (Exception ignored) {
            // Return null so LocaraEngine.heal preserves the real XML_PARSE_ERROR status instead of NO_NODES_FOUND
            return null;
        }
    }

    private static Object requireDriver() {
        Object driver = KatalonDriverBridge.getMobileDriver();
        if (driver == null) {
            throw new RuntimeException(LOG_PREFIX + " No active Appium/MobileDriver found in Katalon context!");
        }
        return driver;
    }

    private static String requirePageSource(Object driver) {
        String pageSource = KatalonDriverBridge.getPageSource(driver);
        if (pageSource == null || pageSource.isEmpty()) {
            throw new RuntimeException(LOG_PREFIX + " Failed to capture getPageSource() from active driver!");
        }
        return pageSource;
    }
}
