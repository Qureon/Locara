package com.qureon.locara.core;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.generator.DynamicXPathGenerator;
import com.qureon.locara.core.generator.KatalonRsSynthesizer;
import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.core.parser.PageSourceParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HybridWebViewHealingTest {

    @Test
    @DisplayName("Should autonomously heal missing element in Hybrid WebView HTML DOM")
    void testHealHybridWebViewElement() throws Exception {
        String htmlSource = "<!DOCTYPE html>"
                + "<html>"
                + "<body>"
                + "  <div class=\"container\">"
                + "    <h2 class=\"header\">Pembayaran Listrik PLN</h2>"
                + "    <div class=\"input-box\">"
                + "      <input type=\"text\" id=\"cust-id\" placeholder=\"Nomor Pelanggan\" />"
                + "    </div>"
                + "    <div class=\"btn-bar\">"
                + "      <button type=\"button\" id=\"btn-cancel\" class=\"secondary\">Batal</button>"
                + "      <button type=\"button\" id=\"btn-submit-pay\" data-testid=\"btn-confirm-pln\" class=\"primary\">"
                + "        Konfirmasi Pembayaran"
                + "      </button>"
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";

        PageSourceParser parser = new PageSourceParser();
        List<UIElementNode> nodes = parser.parse(htmlSource);

        assertNotNull(nodes);
        assertFalse(nodes.isEmpty());

        LocaraEngine engine = new LocaraEngine(new HealConfig());
        HealContext context = HealContext.builder()
                .targetHint("Konfirmasi Pembayaran")
                .action("TAP")
                .screenName("PLNPayment")
                .targetRsPath("Object Repository/PLN/btn_konfirmasi_pembayaran")
                .originalLocator("//button[@id='old_broken_pln_btn']")
                .build();

        HealResult result = engine.heal(htmlSource, context);

        assertTrue(result.isSuccess(), "Locara healing should succeed");
        HealCandidate healed = result.getCandidate();
        assertNotNull(healed, "Locara should successfully find a heal candidate for the button");
        assertTrue(healed.getConfidenceScore() >= 0.70, "Confidence should exceed 70%");
        assertEquals("btn-submit-pay", healed.getNode().getResourceId());
        assertEquals("Konfirmasi Pembayaran", healed.getNode().getText());

        // Verify XPath generated for WebView is clean and semantic
        DynamicXPathGenerator xpathGen = new DynamicXPathGenerator();
        String generatedXPath = xpathGen.generateOptimalXPath(healed.getNode(), nodes, "TAP");
        assertTrue(generatedXPath.contains("data-testid='btn-confirm-pln'") || generatedXPath.contains("id='btn-submit-pay'") || generatedXPath.contains("text()="),
                "XPath should use data-testid, id, or text strategy: " + generatedXPath);

        // Verify Katalon WebElement XML synthesis
        KatalonRsSynthesizer synthesizer = new KatalonRsSynthesizer();
        String webXml = synthesizer.generateWebXmlContent("btn_konfirmasi_pembayaran", healed, context, null);
        assertNotNull(webXml);
        assertTrue(webXml.contains("<WebElementEntity>"));
        assertTrue(webXml.contains("<key>XPATH</key>"));
    }
}
