package com.zerohealing.core.generator;

import com.zerohealing.core.model.HealCandidate;
import com.zerohealing.core.model.HealConfig;
import com.zerohealing.core.model.HealContext;
import com.zerohealing.core.model.UIElementNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class KatalonRsSynthesizerTest {

    private final KatalonRsSynthesizer synthesizer = new KatalonRsSynthesizer();

    @Test
    @DisplayName("Should synthesize ACCESSIBILITY strategy when content-desc exists (Topup & Bayar case)")
    void testAccessibilityStrategy(@TempDir Path tempDir) throws Exception {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("package", "com.bankmega.msmiledev");
        attrs.put("class", "android.view.ViewGroup");
        attrs.put("content-desc", "Topup & Bayar");
        attrs.put("clickable", "true");
        attrs.put("bounds", "[54,800][284,1030]");

        UIElementNode node = new UIElementNode(
                "android.view.ViewGroup",
                "",
                "",
                "Topup & Bayar",
                "android.view.ViewGroup",
                "//android.view.ViewGroup[@content-desc='Topup & Bayar']",
                new Rectangle(54, 800, 230, 230),
                true,
                true,
                true,
                false,
                false,
                5,
                attrs
        );

        HealCandidate candidate = new HealCandidate(node, "//android.view.ViewGroup[@content-desc='Topup & Bayar']", 1.0, "High content-desc match");
        HealContext context = HealContext.builder()
                .targetHint("Topup & Bayar")
                .targetRsPath("Object Repository/Dashboard/01. ViewGroup - Top Up dan Bayar")
                .screenName("Dashboard")
                .build();

        HealConfig config = new HealConfig();
        config.setKatalonProjectPath(tempDir.toString());
        config.setAutoGenerateRs(true);
        config.setAllowOverwriteExistingRs(true);

        File rsFile = synthesizer.synthesizeRsFile(candidate, context, config);
        assertNotNull(rsFile);
        assertTrue(rsFile.exists());

        String xml = Files.readString(rsFile.toPath());

        // Root element
        assertTrue(xml.contains("<MobileElementEntity>"));

        // Strategy & Locator
        assertTrue(xml.contains("<locatorStrategy>ACCESSIBILITY</locatorStrategy>"));
        assertTrue(xml.contains("<locator>Topup &amp; Bayar</locator>"));
        assertTrue(xml.contains("<selectorMethod>ACCESSIBILITY</selectorMethod>"));

        // Locator Collection
        assertTrue(xml.contains("<key>ACCESSIBILITY</key>"));
        assertTrue(xml.contains("<value>Topup &amp; Bayar</value>"));
        assertTrue(xml.contains("<key>XPATH</key>"));
        assertTrue(xml.contains("<key>ATTRIBUTES</key>"));
        assertTrue(xml.contains("<key>ANDROID_UI_AUTOMATOR</key>"));
        assertTrue(xml.contains("new UiSelector().className(&quot;android.view.ViewGroup&quot;).description(&quot;Topup &amp; Bayar&quot;)"));

        // Properties
        assertTrue(xml.contains("<name>class</name>"));
        assertTrue(xml.contains("<name>content-desc</name>"));
        assertTrue(xml.contains("<name>package</name>"));
        assertTrue(xml.contains("<value>com.bankmega.msmiledev</value>"));
        assertTrue(xml.contains("<name>clickable</name>"));
        assertTrue(xml.contains("<name>bounds</name>"));
        assertTrue(xml.contains("<value>[54,800][284,1030]</value>"));
    }

    @Test
    @DisplayName("Should synthesize ID strategy when unique resource-id exists without content-desc")
    void testIdStrategy() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("package", "com.bankmega.msmile");
        attrs.put("class", "android.widget.Button");
        attrs.put("resource-id", "com.bankmega.msmile:id/btn_login");
        attrs.put("clickable", "true");

        UIElementNode node = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_login",
                "",
                "",
                "android.widget.Button",
                "//*[@resource-id='com.bankmega.msmile:id/btn_login']",
                new Rectangle(100, 200, 300, 100),
                true,
                true,
                true,
                false,
                false,
                3,
                attrs
        );

        HealCandidate candidate = new HealCandidate(node, "//*[@resource-id='com.bankmega.msmile:id/btn_login']", 0.95, "Resource ID match");
        String xml = synthesizer.generateMobileXmlContent("01. Button - Login", candidate, null, null);

        assertTrue(xml.contains("<locatorStrategy>ID</locatorStrategy>"));
        assertTrue(xml.contains("<locator>com.bankmega.msmile:id/btn_login</locator>"));
        assertTrue(xml.contains("<name>resource-id</name>"));
        assertTrue(xml.contains("<value>com.bankmega.msmile:id/btn_login</value>"));
    }

    @Test
    @DisplayName("Should synthesize ATTRIBUTES strategy when text exists without content-desc or clean ID")
    void testAttributesStrategy() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("class", "android.widget.TextView");
        attrs.put("text", "Home");

        UIElementNode node = new UIElementNode(
                "android.widget.TextView",
                "",
                "Home",
                "",
                "android.widget.TextView",
                "//android.widget.TextView[@text='Home']",
                new Rectangle(100, 200, 100, 50),
                false,
                true,
                true,
                false,
                false,
                4,
                attrs
        );

        HealCandidate candidate = new HealCandidate(node, "//android.widget.TextView[@text='Home']", 0.9, "Text match");
        String xml = synthesizer.generateMobileXmlContent("android.widget.TextView - Home", candidate, null, null);

        assertTrue(xml.contains("<locatorStrategy>ATTRIBUTES</locatorStrategy>"));
        assertTrue(xml.contains("<locator>//*[@class = &apos;android.widget.TextView&apos; and (@text = &apos;Home&apos; or . = &apos;Home&apos;)]</locator>"));
        assertTrue(xml.contains("<name>text</name>"));
        assertTrue(xml.contains("<value>Home</value>"));
    }
}
