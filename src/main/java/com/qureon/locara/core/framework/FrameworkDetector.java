package com.qureon.locara.core.framework;

import com.qureon.locara.core.model.UIElementNode;

import java.util.List;

/**
 * Intelligent Mobile UI Framework Fingerprinting Engine.
 * Detects whether the active mobile screen was built with React Native, Jetpack Compose,
 * Classic Native Kotlin/Java XML, Flutter, Hybrid WebView, or iOS XCUITest.
 */
public class FrameworkDetector {

    public FrameworkDetector() {}

    /**
     * Inspects parsed UI nodes and raw XML page source to determine the underlying mobile framework.
     */
    public FrameworkFingerprint detect(List<UIElementNode> nodes, String rawXml) {
        if ((nodes == null || nodes.isEmpty()) && (rawXml == null || rawXml.isEmpty())) {
            return new FrameworkFingerprint(AppFramework.UNKNOWN, 0.0, "Empty hierarchy", 0.0);
        }

        String xml = rawXml != null ? rawXml : "";

        // 1. iOS XCUITest Detection
        if (xml.contains("XCUIElementType") || (nodes != null && nodes.stream().anyMatch(n -> n.getClassName().startsWith("XCUIElementType")))) {
            return new FrameworkFingerprint(AppFramework.IOS_XCUITEST, 1.0, "iOS XCUITest element hierarchy detected", 0.0);
        }

        // 2. Flutter Detection
        if (xml.contains("io.flutter.embedding") || xml.contains("FlutterView") || xml.contains("io.flutter.view")) {
            return new FrameworkFingerprint(AppFramework.FLUTTER, 0.98, "Flutter Engine View signature found in page source", 0.0);
        }

        // 3. Jetpack Compose (Modern Native Kotlin)
        if (xml.contains("androidx.compose.ui.platform.ComposeView") ||
                (nodes != null && nodes.stream().anyMatch(n -> n.getClassName().contains("ComposeView")))) {
            return new FrameworkFingerprint(AppFramework.JETPACK_COMPOSE, 0.95, "Jetpack Compose ComposeView node signature identified", 0.0);
        }

        // 4. Hybrid / WebView (Cordova, Capacitor, Ionic, React Native WebViews, or raw HTML DOM)
        if (WebViewContextDetector.isHtml(xml) || xml.contains("android.webkit.WebView") || xml.contains("org.chromium.content.browser") ||
                (nodes != null && nodes.stream().anyMatch(n -> "html".equalsIgnoreCase(n.getTag()) || "body".equalsIgnoreCase(n.getTag())))) {
            return new FrameworkFingerprint(AppFramework.WEBVIEW_HYBRID, 0.98, "Hybrid WebView / Chromium DOM container detected", 0.0);
        }

        // 5. React Native Detection
        // Marker A: Explicit React packages
        if (xml.contains("com.facebook.react") || xml.contains("ReactViewGroup") || xml.contains("ReactEditText")) {
            return new FrameworkFingerprint(AppFramework.REACT_NATIVE, 1.0, "React Native Facebook package markers identified", 1.0);
        }

        // Marker B: High ViewGroup Density Signature (React Native classic architectural fingerprint)
        if (nodes != null && nodes.size() >= 4) {
            long viewGroupCount = nodes.stream()
                    .filter(n -> "android.view.ViewGroup".equals(n.getClassName()) || n.getClassName().endsWith(".ViewGroup"))
                    .count();
            double viewGroupRatio = (double) viewGroupCount / (double) nodes.size();

            if (viewGroupRatio >= 0.35) {
                return new FrameworkFingerprint(
                        AppFramework.REACT_NATIVE,
                        0.92,
                        String.format("High ViewGroup density (%.1f%% of UI nodes are android.view.ViewGroup)", viewGroupRatio * 100.0),
                        viewGroupRatio
                );
            }
        }

        // 6. Classic Native Android (Kotlin / Java with Android XML layouts)
        return new FrameworkFingerprint(
                AppFramework.NATIVE_ANDROID,
                0.90,
                "Standard Android SDK widgets (Button, EditText, LinearLayout, FrameLayout)",
                0.0
        );
    }
}
