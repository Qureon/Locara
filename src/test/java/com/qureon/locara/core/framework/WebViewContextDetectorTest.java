package com.qureon.locara.core.framework;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WebViewContextDetectorTest {

    @Test
    @DisplayName("Should detect HTML DOM vs Native Mobile XML accurately")
    void testDetectHtmlDom() {
        String htmlDoc = "<!DOCTYPE html><html><body><div id=\"root\"></div></body></html>";
        String htmlFrag = "<div class=\"wrapper\"><button id=\"btn\">Click</button></div>";
        String androidXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><hierarchy rotation=\"0\"><android.widget.FrameLayout bounds=\"[0,0][1080,2400]\"/></hierarchy>";
        String iosXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><AppiumAUT><XCUIElementTypeWindow/></AppiumAUT>";

        assertTrue(WebViewContextDetector.isHtml(htmlDoc));
        assertTrue(WebViewContextDetector.isHtml(htmlFrag));
        assertFalse(WebViewContextDetector.isHtml(androidXml));
        assertFalse(WebViewContextDetector.isHtml(iosXml));
        assertFalse(WebViewContextDetector.isHtml(null));
        assertFalse(WebViewContextDetector.isHtml(""));
    }

    @Test
    @DisplayName("Should identify Appium WebView context names")
    void testWebViewContextNames() {
        assertTrue(WebViewContextDetector.isWebViewContext("WEBVIEW_com.example.app"));
        assertTrue(WebViewContextDetector.isWebViewContext("CHROMIUM_1"));
        assertFalse(WebViewContextDetector.isWebViewContext("NATIVE_APP"));
        assertTrue(WebViewContextDetector.isNativeContext("NATIVE_APP"));
    }
}
