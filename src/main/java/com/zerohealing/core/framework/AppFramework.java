package com.zerohealing.core.framework;

/**
 * Mobile application frameworks detectable from Appium view hierarchies and APK metadata.
 */
public enum AppFramework {
    UNIVERSAL("Universal App-Agnostic (Umum)", "Universal"),
    REACT_NATIVE("React Native (Revamp M-Smile)", "RN"),
    KMP_COMPOSE("Kotlin Multiplatform / Compose (KMP)", "KMP"),
    JETPACK_COMPOSE("Jetpack Compose (Modern Native Kotlin)", "Compose"),
    NATIVE_ANDROID("Native Android (Classic Kotlin / Java XML)", "Android XML"),
    FLUTTER("Flutter (Dart Canvas)", "Flutter"),
    WEBVIEW_HYBRID("Hybrid WebView (Cordova / Capacitor / Ionic)", "WebView"),
    IOS_XCUITEST("iOS Native (Swift / SwiftUI / UIKit)", "iOS"),
    UNKNOWN("Generic Mobile Hierarchy", "Generic");

    private final String displayName;
    private final String shortTag;

    AppFramework(String displayName, String shortTag) {
        this.displayName = displayName;
        this.shortTag = shortTag;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortTag() {
        return shortTag;
    }
}
