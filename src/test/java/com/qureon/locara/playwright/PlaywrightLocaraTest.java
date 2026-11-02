package com.qureon.locara.playwright;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PlaywrightLocaraTest {

    /**
     * Mock Playwright Page implementation for unit testing without requiring
     * external Chromium/WebKit binaries in CI/CD pipeline.
     */
    public static class MockPlaywrightPage {
        private final String htmlContent;
        private final String title;
        private final String url;
        private final Map<String, MockPlaywrightLocator> locators = new HashMap<>();

        private boolean lastClicked = false;
        private String lastFilledText = null;

        public MockPlaywrightPage(String htmlContent, String title, String url) {
            this.htmlContent = htmlContent;
            this.title = title;
            this.url = url;
        }

        public String content() {
            return htmlContent;
        }

        public String title() {
            return title;
        }

        public String url() {
            return url;
        }

        public MockPlaywrightLocator locator(String selector) {
            // If selector matches a broken mock selector, throw or return failing locator
            if (selector.contains("broken") || selector.contains("obsolete")) {
                return new MockPlaywrightLocator(this, selector, false);
            }
            return new MockPlaywrightLocator(this, selector, true);
        }

        public boolean isLastClicked() {
            return lastClicked;
        }

        public String getLastFilledText() {
            return lastFilledText;
        }
    }

    public static class MockPlaywrightLocator {
        private final MockPlaywrightPage page;
        private final String selector;
        private final boolean valid;

        public MockPlaywrightLocator(MockPlaywrightPage page, String selector, boolean valid) {
            this.page = page;
            this.selector = selector;
            this.valid = valid;
        }

        public void click() {
            if (!valid) {
                throw new RuntimeException("Playwright TimeoutError: Element not found for selector: " + selector);
            }
            page.lastClicked = true;
        }

        public void fill(String text) {
            if (!valid) {
                throw new RuntimeException("Playwright TimeoutError: Cannot fill element: " + selector);
            }
            page.lastFilledText = text;
        }

        public String textContent() {
            if (!valid) {
                throw new RuntimeException("Playwright TimeoutError: Element not found: " + selector);
            }
            return "Konfirmasi Pembayaran";
        }

        public boolean isVisible() {
            return valid;
        }

        public int count() {
            return valid ? 1 : 0;
        }
    }

    @Test
    @DisplayName("PlaywrightLocara: Click autonomous healing on mutated HTML button")
    public void testClickAutonomousHealing() {
        String html = "<!DOCTYPE html><html><head><title>Mega Checkout Page</title></head><body>" +
                "<div class='checkout-container'>" +
                "  <h2>Ringkasan Transaksi</h2>" +
                "  <button id='btn-pay-new-2026' class='btn-primary' data-testid='submit-payment-btn'>Bayar Sekarang</button>" +
                "</div>" +
                "</body></html>";

        MockPlaywrightPage page = new MockPlaywrightPage(html, "Mega Checkout Page", "https://checkout.bankmega.com");

        // Old broken selector: "#btn-broken-old-checkout"
        // Semantic hint: "Bayar Sekarang"
        boolean clicked = PlaywrightLocara.click(page, "#btn-broken-old-checkout", "Bayar Sekarang");

        assertTrue(clicked, "PlaywrightLocara should successfully heal and click the mutated button");
        assertTrue(page.isLastClicked(), "Page click event should be recorded");
    }

    @Test
    @DisplayName("PlaywrightLocara: Fill autonomous healing on mutated input field")
    public void testFillAutonomousHealing() {
        String html = "<!DOCTYPE html><html><head><title>Login Internet Banking</title></head><body>" +
                "<form id='login-form'>" +
                "  <label for='user-v2'>User ID atau Email</label>" +
                "  <input type='text' id='user-v2' name='user_credential' placeholder='Masukkan User ID' data-testid='login-input-userid' />" +
                "  <button type='submit'>Masuk</button>" +
                "</form>" +
                "</body></html>";

        MockPlaywrightPage page = new MockPlaywrightPage(html, "Login Internet Banking", "https://ib.bankmega.com/login");

        // Old broken selector: "input#old_user_id_broken"
        // Semantic hint: "Masukkan User ID"
        boolean filled = PlaywrightLocara.fill(page, "input#old_user_id_broken", "mega_user_01", "Masukkan User ID");

        assertTrue(filled, "PlaywrightLocara should heal and fill input with target user ID");
        assertEquals("mega_user_01", page.getLastFilledText(), "Filled text should match argument");
    }

    @Test
    @DisplayName("PlaywrightLocara: Aria-label and data-testid modal dismissal healing")
    public void testAriaLabelAndModalHealing() {
        String html = "<!DOCTYPE html><html><body>" +
                "<div role='dialog' aria-modal='true'>" +
                "  <h3>Pemberitahuan Sistem</h3>" +
                "  <button aria-label='Tutup Dialog' data-testid='modal-close-icon' class='close-btn'>&times;</button>" +
                "</div>" +
                "</body></html>";

        MockPlaywrightPage page = new MockPlaywrightPage(html, "Modal Dialog", "https://app.qureon.com");

        String healed = PlaywrightLocara.healSelector(page, ".modal-close-broken", "Tutup Dialog", "CLICK");
        assertNotNull(healed, "Healed selector should not be null");
        assertTrue(healed.contains("modal-close-icon") || healed.contains("Tutup Dialog") || healed.contains("close-btn"),
                "Healed selector should target aria-label or data-testid: " + healed);
    }

    @Test
    @DisplayName("PlaywrightLocara: TextContent extraction with autonomous healing")
    public void testTextContentHealing() {
        String html = "<!DOCTYPE html><html><body>" +
                "<div id='summary-card'>" +
                "  <span class='label-text'>Status Transaksi</span>" +
                "  <span id='status-badge-v2' class='badge-success'>Konfirmasi Pembayaran</span>" +
                "</div>" +
                "</body></html>";

        MockPlaywrightPage page = new MockPlaywrightPage(html, "Detail Transaksi", "https://app.qureon.com/detail");

        String text = PlaywrightLocara.textContent(page, "#status-broken-id", "Konfirmasi Pembayaran");
        assertNotNull(text, "Text content should not be null");
        assertEquals("Konfirmasi Pembayaran", text, "Text content should match expected string");
    }
}
