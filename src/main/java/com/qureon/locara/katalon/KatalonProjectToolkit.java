package com.qureon.locara.katalon;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.context.ContextualEntityResolver;
import com.qureon.locara.core.context.ResolvedAccount;
import com.qureon.locara.core.driver.AppiumDriverActionBridge;
import com.qureon.locara.core.logging.LocaraLogger;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.katalon.variable.AutoVariableSynthesizer;
import com.qureon.locara.katalon.variable.ScriptVariableAligner;
import com.qureon.locara.katalon.variable.TestCaseVariable;

import java.io.File;
import java.util.List;

/**
 * Supports Katalon bootstrap configuration discovery, account card resolution/tapping,
 * and .tc/.groovy authoring utilities so {@link Locara} stays a concise facade.
 */
final class KatalonProjectToolkit {

    private static final String LOG_PREFIX = "[Locara by Qureon]";
    private static final String[] CONFIG_CANDIDATES = {
            "locara.properties", "qureon.properties",
            "Include/config/locara.properties", "Include/config/qureon.properties"
    };

    private static final ContextualEntityResolver CONTEXT_RESOLVER = new ContextualEntityResolver();
    private static final AutoVariableSynthesizer VARIABLE_SYNTHESIZER = new AutoVariableSynthesizer();
    private static final ScriptVariableAligner SCRIPT_ALIGNER = new ScriptVariableAligner();

    private KatalonProjectToolkit() {}

    static LocaraEngine createDefaultEngine() {
        String userDir = System.getProperty("user.dir");
        File targetProp = null;
        for (String candidate : CONFIG_CANDIDATES) {
            File f = new File(userDir, candidate);
            if (f.exists()) {
                targetProp = f;
                break;
            }
        }
        HealConfig config = HealConfig.loadFromProperties(targetProp);
        if (targetProp != null) {
            LocaraLogger.info(LOG_PREFIX, "Loaded configuration from: " + targetProp.getAbsolutePath());
        }
        return new LocaraEngine(config);
    }

    static ResolvedAccount resolveAccountByNumber(Object driver, String targetAccountNumber) {
        Object active = driver != null ? driver : KatalonDriverBridge.getMobileDriver();
        return active == null ? null : CONTEXT_RESOLVER.resolveAccountByNumber(
                KatalonDriverBridge.getPageSource(active), targetAccountNumber);
    }

    static String getSafeCardTapXPath(Object driver, String targetAccountNumber) {
        Object active = driver != null ? driver : KatalonDriverBridge.getMobileDriver();
        String pageSource = active != null ? KatalonDriverBridge.getPageSource(active) : "";
        return CONTEXT_RESOLVER.resolveSafeCardTapXPath(pageSource, targetAccountNumber);
    }

    static boolean tapAccountCard(Object driver, String targetAccountNumber) {
        Object active = driver != null ? driver : KatalonDriverBridge.getMobileDriver();
        if (active == null) return false;
        String xpath = getSafeCardTapXPath(active, targetAccountNumber);
        if (xpath == null || xpath.isEmpty()) return false;
        try {
            List<?> elements = AppiumDriverActionBridge.findElementsByXPath(active, xpath);
            if (!elements.isEmpty()) {
                AppiumDriverActionBridge.executeElementAction(elements.get(0), "TAP", "");
                LocaraLogger.info(LOG_PREFIX, "💳 Safely tapped account card via: " + xpath);
                return true;
            }
        } catch (Throwable t) {
            LocaraLogger.warn(LOG_PREFIX, "tapAccountCard direct click failed: " + t.getMessage());
        }
        return false;
    }

    static boolean synthesizeTestCaseVariables(File tcFile, List<TestCaseVariable> variables) {
        try {
            return VARIABLE_SYNTHESIZER.synthesizeVariables(tcFile, variables);
        } catch (Exception e) {
            LocaraLogger.error(LOG_PREFIX, "Failed to synthesize variables: " + e.getMessage());
            return false;
        }
    }

    static boolean syncVariableDefault(File tcFile, String varName, String defaultValue) {
        try {
            return VARIABLE_SYNTHESIZER.syncVariableDefault(tcFile, varName, defaultValue);
        } catch (Exception e) {
            LocaraLogger.error(LOG_PREFIX, "Failed to sync variable default: " + e.getMessage());
            return false;
        }
    }

    static boolean alignScript(File scriptFile) {
        try {
            return SCRIPT_ALIGNER.alignScript(scriptFile);
        } catch (Exception e) {
            LocaraLogger.error(LOG_PREFIX, "Failed to align script: " + e.getMessage());
            return false;
        }
    }
}
