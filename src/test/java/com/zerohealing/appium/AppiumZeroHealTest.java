package com.zerohealing.appium;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class AppiumZeroHealTest {

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
    public void testAppiumZeroHealTapWithMockDriver() throws Exception {
        String xml;
        try (InputStream is = getClass().getResourceAsStream("/samples/msmile_transfer_confirm.xml")) {
            assertNotNull(is);
            xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        MockAppiumDriver driver = new MockAppiumDriver(xml);

        // Intentionally broken locator: "btn_broken_id"
        // Semantic hint: "Lanjut"
        boolean success = AppiumZeroHeal.tap(driver, "//android.widget.Button[@id='btn_broken_id']", "Lanjut");

        assertTrue(success, "AppiumZeroHeal should heal and click the element");
        assertTrue(driver.isClicked(), "Mock element click() should be executed");
    }
}
