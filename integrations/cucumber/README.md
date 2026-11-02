# 🥒 Locara for Cucumber BDD by Qureon

> **Autonomous Self-Healing, Semantic Recovery & Generative AI Bridge for Cucumber BDD**  
> Compatible with Cucumber-JVM (Java), `@cucumber/cucumber` (JavaScript/TypeScript), Katalon Cucumber Runner, and Appium/Playwright.

---

## ⚡ Executive Summary

Behavior-Driven Development (BDD) with Cucumber is the industry gold-standard for bridging the communication gap between business stakeholders (Product Owners, Business Analysts) and engineering teams.

However, modern BDD suites suffer from a fatal maintenance bottleneck: **Step Definition Drift & Locator Rot**.
* Business scenarios in `.feature` files remain valid.
* But underneath, the hardcoded element locators inside Step Definitions break whenever developers refactor UI components, wrap elements in new containers, or obfuscate resource IDs.
* Non-technical stakeholders see broken builds (*Red Suites*) and assume functional business logic failed, when in reality only a brittle XPath snapped.

**Locara for Cucumber** eliminates this problem entirely by injecting an **autonomous multi-vector healing layer** into Cucumber Step Definitions:
1. **Zero-Boilerplate Gherkin Execution**: `CucumberLocara.executeGherkinStep(driver, "When Pengguna menekan tombol 'Lanjut Transfer'")` directly resolves human-readable semantics into actions without fragile locator glue code.
2. **First-Class Java & TypeScript Parity**: Universal step definitions available for Cucumber-JVM (Java) and `@cucumber/cucumber` (Node.js).
3. **Cross-Engine Support**: Seamlessly dispatches actions across **Appium Mobile** (Android/iOS), **Selenium WebDriver**, and **Playwright Page**.
4. **Generative AI Accelerator**: Provides the missing ground-truth execution engine for AI-generated Gherkin scenarios (Kobiton AI, TestMu KaneAI, LLM prompt-to-test pipelines).

---

## ☕ Quickstart: Java (Cucumber-JVM)

### Approach 1: Zero-Boilerplate Autonomous Step Definition

Execute raw Gherkin steps directly using Locara's natural language semantic parser:

```java
import com.qureon.locara.cucumber.CucumberLocara;
import io.cucumber.java.en.When;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;

public class MSmileTransferSteps {
    private AndroidDriver driver;

    // AI-Style Autonomous Step: No hardcoded XPath required!
    @When("Pengguna menekan tombol {string}")
    public void tapButton(String buttonHint) {
        CucumberLocara.tap(driver, "", buttonHint);
    }

    @And("Pengguna memasukkan {string} pada kolom {string}")
    public void fillInput(String textValue, String fieldLabel) {
        CucumberLocara.setText(driver, "", textValue, fieldLabel);
    }

    @Then("Pengguna melihat status {string}")
    public void verifyStatus(String expectedText) {
        assertTrue(CucumberLocara.verifyText(driver, "", expectedText));
    }
}
```

### Approach 2: Resilient Fallback for Existing Suites

If you already have existing legacy locators or Katalon Object Repositories:

```java
@When("User clicks on Submit Payment")
public void clickSubmit() {
    // If the old #btn-submit-v1 fails, Locara autonomously heals via hint 'Submit Payment'
    CucumberLocara.tap(driver, "//android.widget.Button[@id='btn-submit-v1']", "Submit Payment");
}
```

---

## 🚀 Quickstart: TypeScript (`@qureon/cucumber-locara`)

Install the package:
```bash
npm install @qureon/cucumber-locara --save-dev
```

Import universal self-healing step definitions in your Cucumber setup (`features/step_definitions/steps.ts`):

```typescript
import '@qureon/cucumber-locara';
```

Now you can write feature files in plain English or Indonesian, and they execute with built-in auto-healing out of the box:

```gherkin
Feature: Checkout Pembayaran Bank Mega

  Scenario: Nasabah melakukan pembayaran tagihan
    Given Pengguna berada di halaman tagihan
    When Pengguna memasukkan "1234567890" pada kolom "Nomor Pelanggan"
    And Pengguna menekan tombol "Bayar Sekarang"
    Then Pengguna melihat status "Transaksi Berhasil"
```

---

## 🤖 The Bridge to Generative AI in Test Automation

Modern LLMs (Claude 3.5 Sonnet, GPT-4o) excel at generating human-readable Gherkin scenarios from user stories and PRDs. However, AI-generated tests historically failed during execution because LLMs do not know the exact runtime XPaths or dynamic UI IDs of your mobile app.

**Locara for Cucumber solves this disconnect**:
1. **LLM generates Gherkin feature files** based on product requirements.
2. **Locara inspects active UI hierarchies** in real time via multi-vector semantic scoring (Text, ARIA, Test-ID, Spatial Proximity).
3. **Execution succeeds autonomously** without needing humans to manually spy and map every button.
