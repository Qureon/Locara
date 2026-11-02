package com.qureon.locara.cucumber;

import com.qureon.locara.appium.AppiumLocaraTest.MockAppiumDriver;
import com.qureon.locara.playwright.PlaywrightLocaraTest.MockPlaywrightPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class CucumberLocaraTest {

    @Test
    @DisplayName("CucumberLocara: Gherkin Step parsing for CLICK interaction")
    public void testGherkinStepParsingClick() {
        CucumberLocara.GherkinStepIntent intent = CucumberLocara.parseStep("When Pengguna menekan tombol \"Lanjut Transfer\"");
        assertEquals(CucumberLocara.GherkinStepIntent.ActionType.CLICK, intent.getActionType());
        assertEquals("Lanjut Transfer", intent.getTargetHint());

        CucumberLocara.GherkinStepIntent intentEn = CucumberLocara.parseStep("And User taps on 'Submit Payment' button");
        assertEquals(CucumberLocara.GherkinStepIntent.ActionType.CLICK, intentEn.getActionType());
        assertEquals("Submit Payment", intentEn.getTargetHint());
    }

    @Test
    @DisplayName("CucumberLocara: Gherkin Step parsing for SET_TEXT input")
    public void testGherkinStepParsingInput() {
        CucumberLocara.GherkinStepIntent intent = CucumberLocara.parseStep("And User fills \"mega_user_01\" into \"Username\" field");
        assertEquals(CucumberLocara.GherkinStepIntent.ActionType.SET_TEXT, intent.getActionType());
        assertEquals("mega_user_01", intent.getValue());
        assertEquals("Username", intent.getTargetHint());

        CucumberLocara.GherkinStepIntent intentId = CucumberLocara.parseStep("When pada kolom \"M-PIN\" masukkan \"123456\"");
        assertEquals(CucumberLocara.GherkinStepIntent.ActionType.SET_TEXT, intentId.getActionType());
        assertEquals("123456", intentId.getValue());
        assertEquals("M-PIN", intentId.getTargetHint());
    }

    @Test
    @DisplayName("CucumberLocara: Gherkin Step parsing for VERIFY checkpoint")
    public void testGherkinStepParsingVerify() {
        CucumberLocara.GherkinStepIntent intent = CucumberLocara.parseStep("Then Transaksi berstatus \"Transaksi Berhasil\"");
        assertEquals(CucumberLocara.GherkinStepIntent.ActionType.VERIFY, intent.getActionType());
        assertEquals("Transaksi Berhasil", intent.getTargetHint());
    }

    @Test
    @DisplayName("CucumberLocara: Autonomous Gherkin step execution on Mobile Appium driver")
    public void testExecuteGherkinStepWithMockMobileDriver() throws Exception {
        String xml;
        try (InputStream is = getClass().getResourceAsStream("/samples/msmile_transfer_confirm.xml")) {
            assertNotNull(is);
            xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        MockAppiumDriver driver = new MockAppiumDriver(xml);

        // Broken fallback locator: "//android.widget.Button[@id='broken_btn_id']"
        // Gherkin step contains natural language semantic hint: "Lanjut"
        boolean success = CucumberLocara.executeGherkinStep(
                driver,
                "When Pengguna menekan tombol \"Lanjut\"",
                "//android.widget.Button[@id='broken_btn_id']"
        );

        assertTrue(success, "CucumberLocara should autonomously parse Gherkin and heal the click action on mobile");
        assertTrue(driver.isClicked(), "Underlying driver click event should be recorded");
    }

    @Test
    @DisplayName("CucumberLocara: Autonomous Gherkin step execution on Web Playwright page")
    public void testExecuteGherkinStepWithMockPlaywrightPage() {
        String html = "<!DOCTYPE html><html><body>" +
                "<div class='container'>" +
                "  <button id='btn-checkout-v2' class='btn-primary'>Bayar Sekarang</button>" +
                "</div>" +
                "</body></html>";

        MockPlaywrightPage page = new MockPlaywrightPage(html, "Checkout Page", "https://app.qureon.com");

        // Broken fallback selector: "#btn-broken-old-checkout"
        boolean success = CucumberLocara.executeGherkinStep(
                page,
                "When User clicks on \"Bayar Sekarang\" button",
                "#btn-broken-old-checkout"
        );

        assertTrue(success, "CucumberLocara should autonomously parse Gherkin and heal on Playwright web page");
        assertTrue(page.isLastClicked(), "Playwright page click should be executed");
    }
}
