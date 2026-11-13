package com.qureon.locara.cucumber;

import com.qureon.locara.appium.AppiumLocara;
import com.qureon.locara.playwright.PlaywrightLocara;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First-Class Autonomous BDD & Cucumber Self-Healing Engine.
 *
 * Bridges Behavior-Driven Development (BDD) natural language specifications (Gherkin: Given, When, Then)
 * directly with resilient multi-vector UI healing across Appium (Mobile), Selenium (Web),
 * and Playwright (Modern Web) test runners.
 *
 * Eliminates Step Definition Drift and Locator Rot in enterprise BDD test suites.
 */
public class CucumberLocara {

    private static final Pattern QUOTED_PATTERN = Pattern.compile("[\"']([^\"']+)[\"']");
    private static final Pattern GHERKIN_PREFIX_PATTERN = Pattern.compile("^(?i)(Given|When|Then|And|But|\\*)\\s+");
    private static final Pattern QUOTED_TOKEN_REMOVE_PATTERN = Pattern.compile("[\"'][^\"']+[\"']");
    private static final Pattern SET_TEXT_NOISE_PATTERN = Pattern.compile("(?i)\\b(fill|type|into|field|kolom|input|masukkan|pada)\\b");
    private static final Pattern CLICK_NOISE_PATTERN = Pattern.compile("(?i)\\b(button|tombol|klik|click|tap|tekan|pilih|verify|assert|harus|tampil)\\b");

    private static final Pattern CLICK_PATTERN = Pattern.compile("(?i)\\b(click|clicks|clicked|clicking|tap|taps|tapped|tapping|tekan|menekan|pilih|memilih|klik|mengklik|press|presses|pressed|touch|touches)\\b");
    private static final Pattern INPUT_PATTERN = Pattern.compile("(?i)\\b(fill|fills|filled|filling|type|types|typed|typing|masukkan|memasukkan|input|inputs|ketik|mengetik|isi|mengisi|enter|enters|set|sets)\\b");
    private static final Pattern VERIFY_PATTERN = Pattern.compile("(?i)\\b(verify|verifies|verified|assert|asserts|melihat|memastikan|check|checks|see|sees|tampil|menampilkan|muncul|terlihat|berstatus|status|should|displays|shows|is)\\b");

    /**
     * Encapsulates the parsed semantic intent of a single Gherkin step.
     */
    public static class GherkinStepIntent {
        public enum ActionType {
            CLICK,
            SET_TEXT,
            VERIFY,
            UNKNOWN
        }

        private final ActionType actionType;
        private final String targetHint;
        private final String value;
        private final String rawStep;

        public GherkinStepIntent(ActionType actionType, String targetHint, String value, String rawStep) {
            this.actionType = actionType;
            this.targetHint = targetHint != null ? targetHint.trim() : "";
            this.value = value != null ? value.trim() : "";
            this.rawStep = rawStep != null ? rawStep.trim() : "";
        }

        public ActionType getActionType() { return actionType; }
        public String getTargetHint() { return targetHint; }
        public String getValue() { return value; }
        public String getRawStep() { return rawStep; }

        @Override
        public String toString() {
            return String.format("GherkinStepIntent{action=%s, targetHint='%s', value='%s'}",
                    actionType, targetHint, value);
        }
    }

    /**
     * Parses a raw Gherkin step sentence into a structured semantic execution intent.
     * Extracts semantic target labels, input values, and intended action types.
     *
     * Examples:
     * - "When Pengguna menekan tombol \"Lanjut Transfer\"" -> Action: CLICK, Target: "Lanjut Transfer"
     * - "And User fills \"mega_user_01\" into \"Username\" field" -> Action: SET_TEXT, Value: "mega_user_01", Target: "Username"
     * - "Then Transaksi berstatus \"Berhasil\"" -> Action: VERIFY, Target: "Berhasil"
     */
    public static GherkinStepIntent parseStep(String rawStep) {
        if (rawStep == null || rawStep.trim().isEmpty()) {
            return new GherkinStepIntent(GherkinStepIntent.ActionType.UNKNOWN, "", "", "");
        }

        // 1. Strip Gherkin step keywords
        String cleaned = GHERKIN_PREFIX_PATTERN.matcher(rawStep.trim()).replaceFirst("");

        // 2. Extract quoted arguments
        List<String> quotedArgs = new ArrayList<>();
        Matcher matcher = QUOTED_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            quotedArgs.add(matcher.group(1));
        }

        // 3. Classify Action Type
        GherkinStepIntent.ActionType actionType = GherkinStepIntent.ActionType.UNKNOWN;
        if (INPUT_PATTERN.matcher(cleaned).find()) {
            actionType = GherkinStepIntent.ActionType.SET_TEXT;
        } else if (CLICK_PATTERN.matcher(cleaned).find()) {
            actionType = GherkinStepIntent.ActionType.CLICK;
        } else if (VERIFY_PATTERN.matcher(cleaned).find()) {
            actionType = GherkinStepIntent.ActionType.VERIFY;
        }

        // 4. Resolve Target Hint and Value
        String targetHint = "";
        String value = "";

        if (actionType == GherkinStepIntent.ActionType.SET_TEXT) {
            if (quotedArgs.size() >= 2) {
                // Heuristic: Determine which argument is value vs field label
                // e.g., "fills '123456' into 'PIN'" -> val='123456', hint='PIN'
                // e.g., "pada field 'PIN' masukkan '123456'" -> hint='PIN', val='123456'
                int firstIdx = cleaned.indexOf("'" + quotedArgs.get(0) + "'");
                if (firstIdx == -1) firstIdx = cleaned.indexOf("\"" + quotedArgs.get(0) + "\"");

                String beforeFirst = firstIdx > 0 ? cleaned.substring(0, firstIdx).toLowerCase(java.util.Locale.ROOT) : "";
                if (beforeFirst.contains("field") || beforeFirst.contains("kolom") || beforeFirst.contains("pada")) {
                    targetHint = quotedArgs.get(0);
                    value = quotedArgs.get(1);
                } else {
                    value = quotedArgs.get(0);
                    targetHint = quotedArgs.get(1);
                }
            } else if (quotedArgs.size() == 1) {
                value = quotedArgs.get(0);
                String unquoted = QUOTED_TOKEN_REMOVE_PATTERN.matcher(cleaned).replaceAll("");
                targetHint = SET_TEXT_NOISE_PATTERN.matcher(unquoted).replaceAll("").trim();
            }
        } else {
            // CLICK or VERIFY
            if (!quotedArgs.isEmpty()) {
                targetHint = quotedArgs.get(0);
            } else {
                targetHint = CLICK_NOISE_PATTERN.matcher(cleaned).replaceAll("").trim();
            }
        }

        return new GherkinStepIntent(actionType, targetHint, value, rawStep);
    }

    /**
     * Zero-Boilerplate Autonomous Gherkin Step Execution.
     * Automatically parses Gherkin step semantics and executes with self-healing across any driver.
     *
     * @param driver         Underlying active driver (Appium, Selenium WebDriver, or Playwright Page)
     * @param gherkinStep    Raw Gherkin sentence, e.g., "When User taps on \"Bayar Sekarang\" button"
     * @param fallbackLocator Optional fallback selector or XPath (can be empty string or null)
     * @return true if successfully executed (original or healed)
     */
    public static boolean executeGherkinStep(Object driver, String gherkinStep, String fallbackLocator) {
        GherkinStepIntent intent = parseStep(gherkinStep);
        String locator = fallbackLocator != null ? fallbackLocator : "";

        System.out.println(String.format("[CucumberLocara] Executing Step: '%s' -> Action: %s, Target: '%s'",
                gherkinStep, intent.getActionType(), intent.getTargetHint()));

        switch (intent.getActionType()) {
            case CLICK:
                return tap(driver, locator, intent.getTargetHint());

            case SET_TEXT:
                return setText(driver, locator, intent.getValue(), intent.getTargetHint());

            case VERIFY:
                return verifyText(driver, locator, intent.getTargetHint());

            default:
                // Fallback attempt: if target hint exists, attempt tap or verify
                if (!intent.getTargetHint().isEmpty()) {
                    return tap(driver, locator, intent.getTargetHint());
                }
                System.err.println("[CucumberLocara] Unable to determine actionable intent from Gherkin step: " + gherkinStep);
                return false;
        }
    }

    /**
     * Clicks or taps a target element with autonomous healing for Cucumber Step Definitions.
     */
    public static boolean tap(Object driver, String locatorOrXPath, String targetHint) {
        if (driver == null) {
            throw new IllegalArgumentException("[CucumberLocara] Driver instance cannot be null!");
        }

        if (isPlaywrightPage(driver)) {
            return PlaywrightLocara.click(driver, locatorOrXPath, targetHint);
        } else {
            return AppiumLocara.tap(driver, locatorOrXPath, targetHint);
        }
    }

    /**
     * Types or fills text into an input element with autonomous healing for Cucumber Step Definitions.
     */
    public static boolean setText(Object driver, String locatorOrXPath, String text, String targetHint) {
        if (driver == null) {
            throw new IllegalArgumentException("[CucumberLocara] Driver instance cannot be null!");
        }

        if (isPlaywrightPage(driver)) {
            return PlaywrightLocara.fill(driver, locatorOrXPath, text, targetHint);
        } else {
            return AppiumLocara.setText(driver, locatorOrXPath, text, targetHint);
        }
    }

    /**
     * Verifies that expected text or status is present and visible on screen with autonomous healing.
     */
    public static boolean verifyText(Object driver, String locatorOrXPath, String expectedText) {
        if (driver == null) return false;

        if (isPlaywrightPage(driver)) {
            return PlaywrightLocara.isVisible(driver, locatorOrXPath, expectedText);
        } else {
            try {
                Object element = AppiumLocara.findElement(driver, locatorOrXPath, expectedText);
                return element != null;
            } catch (RuntimeException e) {
                return false;
            }
        }
    }

    /**
     * Checks if a given driver instance is a Microsoft Playwright Page object.
     */
    private static boolean isPlaywrightPage(Object driver) {
        if (driver == null) return false;
        String className = driver.getClass().getName();
        if (className.contains("playwright") && className.contains("Page")) {
            return true;
        }

        // Duck-typing probe: Playwright Page has locator(String) and content()
        try {
            driver.getClass().getMethod("locator", String.class);
            driver.getClass().getMethod("content");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
