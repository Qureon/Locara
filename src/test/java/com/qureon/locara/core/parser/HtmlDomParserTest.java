package com.qureon.locara.core.parser;

import com.qureon.locara.core.model.UIElementNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HtmlDomParserTest {

    @Test
    @DisplayName("Should parse standard hybrid WebView HTML DOM and extract semantic nodes")
    void testParseStandardHtmlDom() {
        String html = "<!DOCTYPE html>"
                + "<html>"
                + "<head><title>Hybrid App Checkout</title></head>"
                + "<body>"
                + "  <div class=\"checkout-container\" id=\"app-root\">"
                + "    <h1 class=\"title\">Konfirmasi Pembayaran</h1>"
                + "    <div class=\"form-group\">"
                + "      <input type=\"text\" id=\"promo-code\" name=\"promoCode\" placeholder=\"Masukkan Kode Promo\" />"
                + "      <button id=\"apply-promo\" data-testid=\"btn-apply-promo\">Terapkan</button>"
                + "    </div>"
                + "    <div class=\"actions\">"
                + "      <a href=\"#\" id=\"btn-cancel\" role=\"button\">Batal</a>"
                + "      <button type=\"submit\" id=\"btn-pay\" data-testid=\"btn-submit-payment\" aria-label=\"Bayar Sekarang\">"
                + "        Bayar Tagihan"
                + "      </button>"
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";

        HtmlDomParser parser = new HtmlDomParser();
        List<UIElementNode> nodes = parser.parse(html);

        assertNotNull(nodes);
        assertFalse(nodes.isEmpty());

        // Find the pay button
        UIElementNode payBtn = nodes.stream()
                .filter(n -> "btn-pay".equals(n.getResourceId()) || "btn-submit-payment".equals(n.getContentDesc()))
                .findFirst()
                .orElse(null);

        assertNotNull(payBtn, "Submit payment button must be parsed");
        assertEquals("button", payBtn.getTag());
        assertEquals("btn-pay", payBtn.getResourceId());
        assertEquals("Bayar Tagihan", payBtn.getText());
        assertEquals("Bayar Sekarang", payBtn.getContentDesc()); // aria-label takes precedence or is extracted
        assertTrue(payBtn.isClickable());
        assertTrue(payBtn.isDisplayed());
        assertTrue(payBtn.isEnabled());

        // Find the input element (void tag)
        UIElementNode promoInput = nodes.stream()
                .filter(n -> "promo-code".equals(n.getResourceId()))
                .findFirst()
                .orElse(null);

        assertNotNull(promoInput, "Promo input must be parsed");
        assertEquals("input", promoInput.getTag());
        assertEquals("Masukkan Kode Promo", promoInput.getText()); // placeholder captured as text
    }

    @Test
    @DisplayName("Should extract data-testid as contentDesc when aria-label is absent")
    void testDataTestIdExtraction() {
        String html = "<div><button data-testid=\"quick-transfer-btn\">Transfer Cepat</button></div>";
        HtmlDomParser parser = new HtmlDomParser();
        List<UIElementNode> nodes = parser.parse(html);

        UIElementNode btn = nodes.stream()
                .filter(n -> "button".equals(n.getTag()))
                .findFirst()
                .orElse(null);

        assertNotNull(btn);
        assertEquals("quick-transfer-btn", btn.getContentDesc());
        assertEquals("Transfer Cepat", btn.getText());
    }
}
