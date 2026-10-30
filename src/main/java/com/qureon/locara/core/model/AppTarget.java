package com.qureon.locara.core.model;

/**
 * High-level execution profile target for Locara by Qureon.
 * Allows explicitly selecting or auto-adapting between:
 * - UMUM (Universal, App-Agnostic, E-Commerce, Fintech, Native)
 * - REVAMP (React Native, Revamp M-Smile, Nested ViewGroups, Text/Desc Priority)
 * - KMP (Kotlin Multiplatform / Compose, TestID Taxonomy, Scrambled PIN, Dynamic Tokens)
 */
public enum AppTarget {
    AUTO("Adaptive Auto-Detection", "AUTO"),
    UMUM("Universal App-Agnostic (Umum)", "UMUM"),
    REVAMP("React Native (Revamp M-Smile)", "REVAMP"),
    KMP("Kotlin Multiplatform (KMP Compose)", "KMP");

    private final String description;
    private final String tag;

    AppTarget(String description, String tag) {
        this.description = description;
        this.tag = tag;
    }

    public String getDescription() {
        return description;
    }

    public String getTag() {
        return tag;
    }

    public static AppTarget fromString(String val) {
        if (val == null) return AUTO;
        String s = val.trim().toUpperCase();
        if (s.contains("REVAMP") || s.contains("REACT")) return REVAMP;
        if (s.contains("KMP") || s.contains("COMPOSE")) return KMP;
        if (s.contains("UMUM") || s.contains("UNIVERSAL") || s.contains("GENERIC")) return UMUM;
        return AUTO;
    }
}
