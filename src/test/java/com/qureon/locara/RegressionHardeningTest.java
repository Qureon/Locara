package com.qureon.locara;

import com.qureon.locara.appium.AppiumLocara;
import com.qureon.locara.appium.AppiumLocaraTest.MockAppiumDriver;
import com.qureon.locara.core.context.ContextualEntityResolver;
import com.qureon.locara.core.context.ResolvedAccount;
import com.qureon.locara.core.generator.KatalonRsSynthesizer;
import com.qureon.locara.core.ledger.HealingAuditLedger;
import com.qureon.locara.core.matcher.KmpMorphologyResolver;
import com.qureon.locara.core.matcher.UniversalSemanticDictionary;
import com.qureon.locara.core.model.AppTarget;
import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.core.parser.HtmlDomParser;
import com.qureon.locara.core.parser.PageSourceParser;
import com.qureon.locara.core.sanitizer.LocaraOrSanitizer;
import com.qureon.locara.cucumber.CucumberLocara;
import com.qureon.locara.katalon.Locara;
import com.qureon.locara.playwright.PlaywrightLocara;
import com.qureon.locara.playwright.PlaywrightLocaraTest.MockPlaywrightPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class RegressionHardeningTest {

    @Test
    @DisplayName("False-green guards return false when target objects or drivers fail verification")
    public void testFalseGreenNegativeGuards() {
        assertFalse(Locara.healAndVerifyPresent(null, 1, "Submit Button"));
        assertFalse(Locara.healAndVerifyVisible(null, 1, "Submit Button"));
        assertFalse(CucumberLocara.verifyText(null, "//*[@text='Lanjut']", "Lanjut"));

        AppiumLocara.getEngine().getConfig().setAuditLedgerEnabled(false);
        PlaywrightLocara.getEngine().getConfig().setAuditLedgerEnabled(false);

        String emptyMobileXml = "<hierarchy><android.widget.TextView text=\"Saldo\" /></hierarchy>";
        MockAppiumDriver mobileDriver = new MockAppiumDriver(emptyMobileXml);
        assertFalse(CucumberLocara.verifyText(mobileDriver, "//android.widget.Button[@id='broken_id']", "Absurd Missing CTA XYZ"));

        MockPlaywrightPage page = new MockPlaywrightPage("<html><body><div>Footer</div></body></html>", "Home", "https://app.example.test");
        assertFalse(CucumberLocara.verifyText(page, "#broken-selector", "Absurd Missing CTA XYZ"));
    }

    @Test
    @DisplayName("ThreadLocal AppTarget isolates parallel threads without leaking global profile")
    public void testThreadLocalAppTargetIsolation() throws Exception {
        AppTarget previousGlobal = Locara.getAppTarget();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Locara.setAppTarget(AppTarget.AUTO);

            Callable<AppTarget[]> kmpWorker = () -> {
                Locara.setThreadAppTarget(AppTarget.KMP);
                AppTarget assigned = Locara.getAppTarget();
                Thread.sleep(50);
                AppTarget afterSleep = Locara.getAppTarget();
                Locara.clearThreadAppTarget();
                AppTarget afterClear = Locara.getAppTarget();
                return new AppTarget[]{assigned, afterSleep, afterClear};
            };

            Callable<AppTarget[]> revampWorker = () -> {
                Locara.setThreadAppTarget(AppTarget.REVAMP);
                AppTarget assigned = Locara.getAppTarget();
                Thread.sleep(50);
                AppTarget afterSleep = Locara.getAppTarget();
                Locara.clearThreadAppTarget();
                AppTarget afterClear = Locara.getAppTarget();
                return new AppTarget[]{assigned, afterSleep, afterClear};
            };

            Future<AppTarget[]> f1 = pool.submit(kmpWorker);
            Future<AppTarget[]> f2 = pool.submit(revampWorker);

            AppTarget[] r1 = f1.get();
            AppTarget[] r2 = f2.get();

            assertEquals(AppTarget.KMP, r1[0]);
            assertEquals(AppTarget.KMP, r1[1]);
            assertEquals(AppTarget.AUTO, r1[2]);

            assertEquals(AppTarget.REVAMP, r2[0]);
            assertEquals(AppTarget.REVAMP, r2[1]);
            assertEquals(AppTarget.AUTO, r2[2]);
            assertEquals(AppTarget.AUTO, Locara.getAppTarget());
        } finally {
            Locara.clearThreadAppTarget();
            Locara.setAppTarget(previousGlobal);
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("HealConfig loads locara.scroll.max_attempts and locara.target from properties")
    public void testHealConfigLoadFromProperties(@TempDir Path tempDir) throws IOException {
        Path propPath = tempDir.resolve("locara.properties");
        Files.writeString(propPath, "locara.scroll.max_attempts=5\nlocara.target=KMP\n", StandardCharsets.UTF_8);

        HealConfig config = HealConfig.loadFromProperties(propPath.toFile());
        assertEquals(5, config.getMaxScrollAttempts());
        assertEquals(AppTarget.KMP, config.getAppTarget());

        config.setThreadAppTarget(AppTarget.REVAMP);
        assertEquals(AppTarget.REVAMP, config.getAppTarget());
        config.clearThreadAppTarget();
        assertEquals(AppTarget.KMP, config.getAppTarget());
    }

    @Test
    @DisplayName("UniversalSemanticDictionary scores multi-word concept phrases and cross-lingual pairs")
    public void testMultiWordSemanticAffinity() {
        double crossLingual = UniversalSemanticDictionary.calculateSemanticAffinity("Pilih Pasar Uang", "Money Market Tab");
        assertTrue(crossLingual >= 0.40, "Expected meaningful cross-lingual affinity, got " + crossLingual);

        double unrelated = UniversalSemanticDictionary.calculateSemanticAffinity("Hapus Riwayat Transaksi", "Money Market Tab");
        assertTrue(crossLingual > unrelated, "Expected related phrase to outscore unrelated phrase");
    }

    @Test
    @DisplayName("KmpMorphologyResolver handles regex metacharacters safely and UIElementNode strips non-bank package IDs")
    public void testKmpMorphologySafeRegexAndPackageStrip() {
        assertTrue(KmpMorphologyResolver.matchesDynamicToken("card[0].item_savings_01", "card[0].item_<id>"));
        assertFalse(KmpMorphologyResolver.matchesDynamicToken("card[0]Xitem_savings_01", "card[0].item_<id>"));

        UIElementNode genericNode = new UIElementNode(
                "android.widget.Button",
                "com.example.shop:id/btn_checkout_now",
                "Checkout",
                "",
                "android.widget.Button",
                "//android.widget.Button[1]",
                new Rectangle(0, 0, 100, 40),
                true, true, true, false, false, 1
        );
        assertEquals("btn_checkout_now", genericNode.getStrippedResourceId());
    }

    @Test
    @DisplayName("ContextualEntityResolver isolates siblings within matched account card and ignores adjacent cards")
    public void testContextualEntityResolverCardIsolation() {
        String xml = "<hierarchy>" +
                "  <android.view.ViewGroup bounds=\"[0,100][1080,400]\">" +
                "    <android.view.ViewGroup bounds=\"[40,120][1040,240]\">" +
                "      <android.widget.TextView text=\"Tabungan Payroll\" bounds=\"[60,130][500,170]\"/>" +
                "      <android.widget.TextView text=\"900011112222333\" bounds=\"[60,180][500,220]\"/>" +
                "    </android.view.ViewGroup>" +
                "    <android.view.ViewGroup bounds=\"[40,260][1040,380]\">" +
                "      <android.widget.TextView text=\"Deposito Berjangka\" bounds=\"[60,270][500,310]\"/>" +
                "      <android.widget.TextView text=\"900044445555666\" bounds=\"[60,320][500,360]\"/>" +
                "    </android.view.ViewGroup>" +
                "  </android.view.ViewGroup>" +
                "</hierarchy>";

        ContextualEntityResolver resolver = new ContextualEntityResolver();
        ResolvedAccount secondCard = resolver.resolveAccountByNumber(xml, "900044445555666");
        assertNotNull(secondCard);
        assertEquals("900044445555666", secondCard.getAccountNumber());
        assertEquals("Deposito Berjangka", secondCard.getAccountType());
    }

    @Test
    @DisplayName("KatalonRsSynthesizer blocks path traversal and escapes XML special chars in descriptions")
    public void testKatalonRsSynthesizerTraversalAndEscaping(@TempDir Path tempDir) {
        UIElementNode buttonNode = new UIElementNode(
                "android.widget.Button",
                "com.example.app:id/btn_pay",
                "Bayar & Lanjut",
                "",
                "android.widget.Button",
                "//android.widget.Button[@text='Bayar & Lanjut']",
                new Rectangle(0, 0, 120, 48),
                true, true, true, false, false, 1
        );
        HealCandidate candidate = new HealCandidate(buttonNode, "//*[@text='Bayar & Lanjut']", 0.95, "Reason <unsafe> & 'quote'");
        HealContext context = HealContext.builder()
                .targetHint("Bayar")
                .targetRsPath("../../outside.rs")
                .build();
        HealConfig config = new HealConfig();
        config.setKatalonProjectPath(tempDir.toAbsolutePath().toString());

        KatalonRsSynthesizer synthesizer = new KatalonRsSynthesizer();
        assertThrows(IOException.class, () -> synthesizer.synthesizeRsFile(candidate, context, config));

        String mobileXml = synthesizer.generateMobileXmlContent("Btn_Bayar", candidate, context, null);
        assertTrue(mobileXml.contains("&lt;unsafe&gt; &amp;"));
        assertFalse(mobileXml.contains("Reason <unsafe> &"));
    }

    @Test
    @DisplayName("LocaraOrSanitizer fixes brittle CRLF xpath properties and updates both <webElementProperties> and <locator>")
    public void testOrSanitizerCrlfUpdate(@TempDir Path tempDir) throws IOException {
        Path rsPath = tempDir.resolve("btn_legacy.rs");
        String legacyRs = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                "<MobileElementEntity>\r\n" +
                "   <name>btn_legacy</name>\r\n" +
                "   <selectorMethod>BASIC</selectorMethod>\r\n" +
                "   <webElementProperties>\r\n" +
                "      <name>text</name>\r\n" +
                "      <value>Konfirmasi</value>\r\n" +
                "   </webElementProperties>\r\n" +
                "   <webElementProperties>\r\n" +
                "      <name>xpath</name>\r\n" +
                "      <type>Main</type>\r\n" +
                "      <value>//hierarchy/android.widget.FrameLayout[1]/android.widget.Button[2]</value>\r\n" +
                "   </webElementProperties>\r\n" +
                "   <locator>//hierarchy/android.widget.FrameLayout[1]/android.widget.Button[2]</locator>\r\n" +
                "   <locatorStrategy>XPATH</locatorStrategy>\r\n" +
                "</MobileElementEntity>\r\n";
        Files.writeString(rsPath, legacyRs, StandardCharsets.UTF_8);

        LocaraOrSanitizer sanitizer = new LocaraOrSanitizer();
        LocaraOrSanitizer.SanitizeResult result = sanitizer.processSingleFile(rsPath.toFile(), true, false);
        assertTrue(result.wasBrittle());
        assertTrue(result.isSanitized());

        String updated = Files.readString(rsPath, StandardCharsets.UTF_8);
        assertFalse(updated.contains("//hierarchy/"));
        assertTrue(updated.contains("Konfirmasi"));
        assertTrue(updated.contains("<selectorMethod>XPATH</selectorMethod>"));
    }

    @Test
    @DisplayName("HealingAuditLedger masks PII and writes canonical locara_audit.json schema only")
    public void testAuditLedgerMaskPiiAndCanonicalJson(@TempDir Path tempDir) throws IOException {
        String maskedEmail = HealingAuditLedger.maskPii("user qa.tester@example.org mpin=654321 card 4111-2222-3333-4444");
        assertFalse(maskedEmail.contains("qa.tester@example.org"));
        assertFalse(maskedEmail.contains("654321"));
        assertFalse(maskedEmail.contains("4111-2222"));

        HealConfig config = new HealConfig();
        config.setKatalonProjectPath(tempDir.toAbsolutePath().toString());
        config.setAuditLogFolder("evidence");
        config.setAuditLedgerEnabled(true);

        UIElementNode node = new UIElementNode(
                "android.widget.Button",
                "com.example.app:id/btn_transfer",
                "Lanjut Transfer",
                "",
                "android.widget.Button",
                "//*[@text='Lanjut Transfer']",
                new Rectangle(0, 0, 120, 48),
                true, true, true, false, false, 1
        );
        HealCandidate candidate = new HealCandidate(node, "//*[@text='Lanjut Transfer']", 0.91, "Semantic Match");
        HealContext context = HealContext.builder()
                .screenName("TransferScreen")
                .action("TAP")
                .targetHint("Lanjut 90001234567890")
                .originalLocator("//*[@id='broken']")
                .build();

        new HealingAuditLedger().recordEvent(context, candidate, new File("Object Repository/btn.rs"), "HEALED", config);

        Path evidenceDir = tempDir.resolve("evidence");
        Path jsonPath = evidenceDir.resolve("locara_audit.json");
        assertTrue(Files.exists(jsonPath));
        assertFalse(Files.exists(evidenceDir.resolve("locara_audit_v2.json")));

        String line = Files.readString(jsonPath, StandardCharsets.UTF_8);
        assertTrue(line.contains("\"targetHint\""));
        assertTrue(line.contains("\"healedLocator\""));
        assertTrue(line.contains("\"generatedRs\""));
        assertFalse(line.contains("90001234567890"));
    }

    @Test
    @DisplayName("PageSourceParser rejects XXE DOCTYPE payloads and HtmlDomParser resists ReDoS and quotes IDs")
    public void testParsersSecurityAndResilience() {
        PageSourceParser xmlParser = new PageSourceParser();
        String xxePayload = "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><hierarchy><node text=\"&xxe;\"/></hierarchy>";
        assertThrows(Exception.class, () -> xmlParser.parse(xxePayload));

        HtmlDomParser htmlParser = new HtmlDomParser();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            String malformed = "<div>" + "<div ".repeat(200) + "class=\"unclosed\"";
            assertNotNull(htmlParser.parse(malformed));
        });

        List<UIElementNode> nodes = htmlParser.parse("<button id=\"o'reilly_btn\">Simpan</button>");
        assertFalse(nodes.isEmpty());
        assertEquals("//button[@id=\"o'reilly_btn\"]", nodes.get(0).getXpath());
    }

    @Test
    @DisplayName("PlaywrightLocara click and fill overloads heal with custom probeTimeoutMs")
    public void testPlaywrightTimeoutOverloads() {
        PlaywrightLocara.getEngine().getConfig().setAuditLedgerEnabled(false);
        String html = "<!DOCTYPE html><html><body>" +
                "<input id='username-v2' placeholder='Username' />" +
                "<button id='btn-submit-v2'>Bayar Sekarang</button>" +
                "</body></html>";

        MockPlaywrightPage page = new MockPlaywrightPage(html, "Checkout", "https://app.example.test/checkout");
        assertTrue(PlaywrightLocara.fill(page, "#obsolete-username", "demo_user_02", "Username", 250));
        assertEquals("demo_user_02", page.getLastFilledText());

        assertTrue(PlaywrightLocara.click(page, "#obsolete-submit", "Bayar Sekarang", 250));
        assertTrue(page.isLastClicked());
    }
}

