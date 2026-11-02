package com.qureon.locara.core.framework;

/**
 * High-speed context and DOM format detector for hybrid mobile applications.
 * Accurately detects whether a given page source payload or Appium context string
 * corresponds to an embedded HTML WebView or a native XML hierarchy.
 */
public class WebViewContextDetector {

    private WebViewContextDetector() {}

    /**
     * Inspects raw page source text to determine whether it is an HTML DOM dump from an embedded WebView.
     */
    public static boolean isHtml(String source) {
        if (source == null || source.trim().isEmpty()) {
            return false;
        }
        String clean = source.trim().toLowerCase();
        
        // Fast prefix match
        if (clean.startsWith("<!doctype html") || clean.startsWith("<html")) {
            return true;
        }

        // XML declaration with HTML root tag
        if (clean.startsWith("<?xml") && clean.contains("<html")) {
            return true;
        }

        // Common HTML structures in WebViews
        if (clean.contains("<head") && clean.contains("<body")) {
            return true;
        }

        if (clean.contains("<body") && clean.contains("</html>")) {
            return true;
        }

        // HTML DOM fragments (e.g. injected DOM, shadow root dumps, WebView fragments)
        if (!clean.contains("<hierarchy") && !clean.contains("<appiumaut") && !clean.contains("android.widget.") && !clean.contains("xcuielementtype")) {
            if (clean.contains("<div") || clean.contains("<button") || clean.contains("<input") || clean.contains("<span") || clean.contains("<a ") || clean.contains("<form")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the active Appium driver context is a WebView context.
     */
    public static boolean isWebViewContext(String contextName) {
        if (contextName == null) return false;
        String upper = contextName.trim().toUpperCase();
        return upper.contains("WEBVIEW") || upper.contains("CHROMIUM");
    }

    /**
     * Checks if the active Appium driver context is the native mobile context.
     */
    public static boolean isNativeContext(String contextName) {
        if (contextName == null) return false;
        return "NATIVE_APP".equalsIgnoreCase(contextName.trim());
    }
}
