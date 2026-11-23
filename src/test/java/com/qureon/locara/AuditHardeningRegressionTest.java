package com.qureon.locara;

import com.qureon.locara.cli.audit.CodeNamingAuditor;
import com.qureon.locara.cli.audit.CodeNamingFinding;
import com.qureon.locara.core.context.ResolvedAccount;
import com.qureon.locara.core.driver.ReflectiveAccessUtil;
import com.qureon.locara.core.guard.DangerKeywordGuard;
import com.qureon.locara.core.guard.OverlayAirbag;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.core.parser.HtmlDomParser;
import com.qureon.locara.core.parser.PageSourceParser;
import com.qureon.locara.cucumber.CucumberLocara;
import com.qureon.locara.katalon.variable.ScriptVariableAligner;
import com.qureon.locara.playwright.PlaywrightLocara;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Audit Hardening Regression Suite (Phases 2-4)")
class AuditHardeningRegressionTest {

    public interface PublicPageContract {
        String content();
        String title();
        PublicLocatorContract locator(String selector);
    }

    public interface PublicLocatorContract {
        void click();
        boolean isVisible();
        int count();
    }

    private static final class PackagePrivateLocatorImpl implements PublicLocatorContract {
        boolean clicked;

        @Override public void click() { this.clicked = true; }
        @Override public boolean isVisible() { return true; }
        @Override public int count() { return 1; }
    }

    private static final class PackagePrivatePageImpl implements PublicPageContract {
        final PackagePrivateLocatorImpl healedLocator = new PackagePrivateLocatorImpl();

        @Override
        public String content() {
            return "<html><body><button id=\"btn_real_pay\" data-testid=\"pay-now\">Bayar Sekarang</button></body></html>";
        }

        @Override
        public String title() {
            return "Checkout";
        }

        @Override
        public PublicLocatorContract locator(String selector) {
            if (selector.contains("broken")) {
                return new PublicLocatorContract() {
                    @Override public void click() { throw new RuntimeException("missing"); }
                    @Override public boolean isVisible() { return false; }
                    @Override public int count() { return 0; }
                };
            }
            return healedLocator;
        }
    }

    @Test
    @DisplayName("ReflectiveAccessUtil and PlaywrightLocara invoke public interface methods on non-public impl classes")
    void reflectiveAccessHandlesNonPublicImplementations() throws Exception {
        Method m = ReflectiveAccessUtil.findAccessibleMethod(PackagePrivatePageImpl.class, "content");
        assertTrue(Modifier.isPublic(m.getDeclaringClass().getModifiers()),
                "Resolved method must belong to an accessible public interface or superclass");

        PackagePrivatePageImpl page = new PackagePrivatePageImpl();
        boolean prevAudit = PlaywrightLocara.getEngine().getConfig().isAuditLedgerEnabled();
        PlaywrightLocara.getEngine().getConfig().setAuditLedgerEnabled(false);
        try {
            boolean clicked = PlaywrightLocara.click(page, "#broken-selector", "Bayar Sekarang");
            assertTrue(clicked);
            assertTrue(page.healedLocator.clicked, "Healed click must dispatch on non-public locator impl");
        } finally {
            PlaywrightLocara.getEngine().getConfig().setAuditLedgerEnabled(prevAudit);
        }
    }

    private static UIElementNode node(String tag, String resourceId, String text, boolean clickable) {
        return new UIElementNode(tag, resourceId, text, "", tag, "", new Rectangle(0, 0, 100, 40),
                clickable, true, true, false, false, 1);
    }

    @Test
    @DisplayName("DangerKeywordGuard enforces token boundaries, widget prefixes, and financial isolation")
    void dangerGuardTokenBoundariesAndIsolation() {
        UIElementNode blockBtn = node("Button", "", "Blokir Kartu", true);
        UIElementNode unblockedInfo = node("TextView", "", "Status Unblocked", false);
        UIElementNode btnHapus = node("Button", "app:id/btnhapus", "Konfirmasi", true);
        UIElementNode bayarBtn = node("Button", "", "Bayar Sekarang", true);

        assertTrue(DangerKeywordGuard.isPolarityViolation("Lanjut", blockBtn));
        assertFalse(DangerKeywordGuard.isPolarityViolation("Status Unblocked", unblockedInfo),
                "Substring 'block' inside 'unblocked' must not trigger false danger lockout");
        assertTrue(DangerKeywordGuard.isPolarityViolation("Konfirmasi", btnHapus),
                "Unseparated widget prefix 'btnhapus' must still trigger danger lockout");
        assertTrue(DangerKeywordGuard.isPolarityViolation("Lanjut", bayarBtn),
                "Rule 0: non-financial target must never click financial action");
        assertFalse(DangerKeywordGuard.isPolarityViolation("Bayar Sekarang", bayarBtn));
    }

    @Test
    @DisplayName("OverlayAirbag rejects Android 14+ deny variants and protects OK buttons on form screens")
    void overlayAirbagAndroid14DenyAndModalContext() {
        OverlayAirbag airbag = new OverlayAirbag();

        UIElementNode denyBtn = node(
                "Button",
                "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button",
                "Don\u2019t allow",
                true
        );
        UIElementNode allowFg = node(
                "Button",
                "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
                "While using the app",
                true
        );

        UIElementNode picked = airbag.findPermissionAllowButton(List.of(denyBtn, allowFg));
        assertSame(allowFg, picked);
        assertNull(airbag.findPermissionAllowButton(List.of(denyBtn)));

        UIElementNode formInput = node("android.widget.EditText", "", "", true);
        UIElementNode formDialogLabel = node("android.widget.TextView", "", "Popup Form", false);
        UIElementNode okBtn = node("android.widget.Button", "", "OK", true);
        assertNull(airbag.findModalDismissButton(List.of(formDialogLabel, formInput, okBtn)),
                "Contextual OK must NOT be dismissed when an EditText input widget is present");

        UIElementNode modalTitle = node("android.widget.TextView", "", "Pemberitahuan Penting", false);
        assertSame(okBtn, airbag.findModalDismissButton(List.of(modalTitle, okBtn)),
                "Contextual OK SHOULD be dismissed on non-input modal advisory");
    }

    @Test
    @DisplayName("HtmlDomParser parses unquoted attributes and decodes HTML entities cleanly")
    void htmlDomParserUnquotedAttributesAndEntities() {
        String html = "<html><body><button id=btn_pay data-testid=submit-order "
                + "aria-label=\"Bayar &amp; Kirim\">Lanjut &gt; &#39;OK&#39;</button></body></html>";
        List<UIElementNode> nodes = new HtmlDomParser().parse(html);
        UIElementNode btn = nodes.stream().filter(n -> "button".equals(n.getTag())).findFirst().orElse(null);

        assertNotNull(btn);
        assertEquals("btn_pay", btn.getResourceId());
        assertEquals("Bayar & Kirim", btn.getContentDesc());
        assertEquals("Lanjut > 'OK'", btn.getText());
    }

    @Test
    @DisplayName("ScriptVariableAligner respects try closing brace and never relocates catch/finally declarations")
    void scriptVariableAlignerRespectsTryClosingBrace() {
        String script = ""
                + "try {\n"
                + "    String token = binding.getVariable('token')\n"
                + "    println('{ brace in string }')\n"
                + "} catch (Exception e) {\n"
                + "    String catchNote = binding.getVariable('catchNote')\n"
                + "}\n"
                + "String afterTry = binding.getVariable('afterTry')\n";

        String aligned = new ScriptVariableAligner().alignScriptContent(script);
        assertTrue(aligned.indexOf("String token") < aligned.indexOf("try {"),
                "Declaration inside try must move before try");
        assertTrue(aligned.indexOf("String catchNote") > aligned.indexOf("catch (Exception e)"),
                "Declaration inside catch must stay inside catch");
        assertTrue(aligned.indexOf("String afterTry") > aligned.indexOf("catch (Exception e)"),
                "Declaration after try-catch must stay after try-catch");
    }

    @Test
    @DisplayName("CodeNamingAuditor scans hidden-prefixed rootDir while skipping hidden child folders")
    void codeNamingAuditorScansRootDirAndSkipsHiddenSubdirs(@TempDir Path tempDir) throws Exception {
        Path hiddenRoot = tempDir.resolve(".workspace-root");
        Path visibleSub = hiddenRoot.resolve("Scripts");
        Path hiddenSub = hiddenRoot.resolve(".git");
        Files.createDirectories(visibleSub);
        Files.createDirectories(hiddenSub);

        Files.writeString(visibleSub.resolve("Main.groovy"), "def temp = 'abc'\n", StandardCharsets.UTF_8);
        Files.writeString(hiddenSub.resolve("Ignored.groovy"), "def temp = 'xyz'\n", StandardCharsets.UTF_8);

        List<CodeNamingFinding> findings = new CodeNamingAuditor().auditProject(hiddenRoot.toFile());
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).getFile().contains("Main.groovy"));
    }

    @Test
    @DisplayName("HealConfig UTF-8 loader preserves defaults on invalid numeric values and masks ResolvedAccount PII")
    void healConfigInvalidNumbersAndResolvedAccountMasking(@TempDir Path tempDir) throws Exception {
        Path props = tempDir.resolve("locara.properties");
        Files.writeString(props, ""
                + "locara.threshold=not-a-number\n"
                + "locara.scroll.max_attempts=abc\n"
                + "locara.target=KMP\n"
                + "locara.project.path=/tmp/proyek-utf8-\u00e9\n", StandardCharsets.UTF_8);

        HealConfig cfg = HealConfig.loadFromProperties(props.toFile());
        assertEquals(0.70, cfg.getMinConfidenceThreshold(), 0.0001);
        assertEquals(2, cfg.getMaxScrollAttempts());
        assertEquals("KMP", cfg.getAppTarget().name());
        assertEquals("/tmp/proyek-utf8-\u00e9", cfg.getKatalonProjectPath());

        ResolvedAccount acc = new ResolvedAccount("900012345678", "Mega Dana", "Budi Santoso", true, null);
        String rendered = acc.toString();
        assertFalse(rendered.contains("900012345678"), "Raw account number must never appear in toString()");
        assertFalse(rendered.contains("Budi Santoso"), "Account holder name must be masked in toString()");
        assertTrue(rendered.contains("****5678"));
    }

    @Test
    @DisplayName("CucumberLocara classifies quoted action words accurately and rejects null/unparseable steps")
    void cucumberLocaraQuotedClassificationAndNoFalseGreen() {
        CucumberLocara.GherkinStepIntent clickIsiPulsa =
                CucumberLocara.parseStep("When Pengguna menekan tombol \"Isi Pulsa\"");
        assertEquals(CucumberLocara.GherkinStepIntent.ActionType.CLICK, clickIsiPulsa.getActionType(),
                "Quoted 'Isi' inside button label must not misclassify CLICK as SET_TEXT");
        assertEquals("Isi Pulsa", clickIsiPulsa.getTargetHint());

        assertFalse(CucumberLocara.verifyText(null, "//*[@text='OK']", "OK"));
        assertFalse(CucumberLocara.executeGherkinStep(null, "Given   ", null));
        assertThrows(IllegalArgumentException.class, () -> CucumberLocara.tap(null, "#btn", "Bayar"));
    }

    @Test
    @DisplayName("PageSourceParser fails closed on XXE DOCTYPE/ENTITY payloads")
    void pageSourceParserFailsClosedOnXxe() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<hierarchy><android.widget.TextView text=\"&xxe;\"/></hierarchy>";
        assertThrows(Exception.class, () -> new PageSourceParser().parse(xxe));
    }

    @Test
    @DisplayName("HealConfig.loadDefault honors root locara.local.properties even when base config is under Include/config")
    void healConfigLoadDefaultHonorsRootLocalOverride(@TempDir Path tempDir) throws Exception {
        Path cfgDir = tempDir.resolve("Include/config");
        Files.createDirectories(cfgDir);
        Files.writeString(cfgDir.resolve("locara.properties"),
                "locara.enabled=true\nlocara.threshold=0.82\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("locara.local.properties"),
                "locara.enabled=false\n", StandardCharsets.UTF_8);

        HealConfig cfg = HealConfig.loadDefault(tempDir.toFile());
        assertFalse(cfg.isEnabled(), "Root locara.local.properties must override Include/config/locara.properties");
        assertEquals(0.82, cfg.getMinConfidenceThreshold(), 0.0001);
    }
}
