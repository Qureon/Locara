package com.qureon.locara.appium;

import com.qureon.locara.katalon.ScrollRecoveryEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class AppiumLocaraTest {

    @BeforeAll
    static void disableAuditLedgerForUnitTests() {
        AppiumLocara.getEngine().getConfig().setAuditLedgerEnabled(false);
    }

    @AfterEach
    void resetScrollState() {
        AppiumLocara.setScrollEngineForTests(null);
        AppiumLocara.clearManualScrollState();
    }

    // Dummy mock driver mimicking AppiumDriver
    public static class MockAppiumDriver {
        private final String pageSource;
        private boolean clicked = false;

        public MockAppiumDriver(String pageSource) {
            this.pageSource = pageSource;
        }

        public String getPageSource() {
            return pageSource;
        }

        public boolean isClicked() {
            return clicked;
        }

        public MockWebElement findElement(Object by) {
            if (by != null && String.valueOf(by).contains("broken")) {
                return null;
            }
            return new MockWebElement(this);
        }
    }

    public static class MockWebElement {
        private final MockAppiumDriver driver;

        public MockWebElement(MockAppiumDriver driver) {
            this.driver = driver;
        }

        public void click() {
            driver.clicked = true;
        }

        public void clear() {}

        public void sendKeys(CharSequence... keys) {}
    }

    @Test
    public void testAppiumLocaraTapWithMockDriver() throws Exception {
        String xml;
        try (InputStream is = getClass().getResourceAsStream("/samples/msmile_transfer_confirm.xml")) {
            assertNotNull(is);
            xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        MockAppiumDriver driver = new MockAppiumDriver(xml);

        // Intentionally broken locator: "btn_broken_id"
        // Semantic hint: "Lanjut Transfer" (satisfies DangerKeywordGuard financial isolation)
        boolean success = AppiumLocara.tap(driver, "//android.widget.Button[@id='btn_broken_id']", "Lanjut Transfer");

        assertTrue(success, "AppiumLocara should heal and click the element");
        assertTrue(driver.isClicked(), "Mock element click() should be executed");
    }

    @Test
    public void testAppiumLocaraPreventsDoubleScrollAfterManualScroll() {
        String topXml = "<hierarchy>"
                + "<android.widget.ScrollView scrollable=\"true\" bounds=\"[0,0][1080,1920]\">"
                + "<android.widget.TextView text=\"Transfer Screen\" bounds=\"[40,100][500,180]\"/>"
                + "</android.widget.ScrollView>"
                + "</hierarchy>";
        String scrolledXml = "<hierarchy>"
                + "<android.widget.ScrollView scrollable=\"true\" bounds=\"[0,0][1080,1920]\">"
                + "<android.widget.Button resource-id=\"btn_confirm_next\" text=\"Konfirmasi Transfer\" clickable=\"true\" bounds=\"[40,1650][1040,1800]\"/>"
                + "</android.widget.ScrollView>"
                + "</hierarchy>";

        AtomicInteger scrollCount = new AtomicInteger(0);
        AppiumLocara.setScrollEngineForTests(new ScrollRecoveryEngine(2) {
            @Override
            public boolean performScrollDown(Object driver) {
                scrollCount.incrementAndGet();
                return true;
            }
        });

        MockAppiumDriver preScrolledDriver = new MockAppiumDriver(topXml);
        AppiumLocara.notifyManualScroll();
        assertThrows(RuntimeException.class, () ->
                AppiumLocara.tap(preScrolledDriver, "//android.widget.Button[@id='broken']", "Konfirmasi Transfer"));
        assertEquals(0, scrollCount.get(), "AppiumLocara must not trigger double scroll after notifyManualScroll()");

        // When no manual scroll is active, ScrollRecoveryEngine reveals the scrolled target and completes click
        AppiumLocara.clearManualScrollState();
        MockAppiumDriver pagingDriver = new MockAppiumDriver(topXml) {
            @Override
            public String getPageSource() {
                return scrollCount.get() > 0 ? scrolledXml : topXml;
            }
        };
        assertTrue(AppiumLocara.tap(pagingDriver, "//android.widget.Button[@id='broken']", "Konfirmasi Transfer"));
        assertEquals(1, scrollCount.get());
        assertTrue(pagingDriver.isClicked());
    }
}
