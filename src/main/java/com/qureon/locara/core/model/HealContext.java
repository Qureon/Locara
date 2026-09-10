package com.qureon.locara.core.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates the execution context of a failed step or a target element to be resolved.
 */
public class HealContext {
    private final String targetHint;
    private final String expectedTag;
    private final String originalLocator;
    private final String screenName;
    private final String targetRsPath;
    private final String action;
    private final String value;
    private final java.io.File rawScreenshot;
    private final Map<String, String> attributes = new HashMap<>();

    private HealContext(Builder builder) {
        this.targetHint = builder.targetHint != null ? builder.targetHint : "";
        this.expectedTag = builder.expectedTag != null ? builder.expectedTag : "";
        this.originalLocator = builder.originalLocator != null ? builder.originalLocator : "";
        this.screenName = builder.screenName != null ? builder.screenName : "DefaultScreen";
        this.targetRsPath = builder.targetRsPath != null ? builder.targetRsPath : "";
        this.action = builder.action != null ? builder.action : "TAP";
        this.value = builder.value != null ? builder.value : "";
        this.rawScreenshot = builder.rawScreenshot;
        this.attributes.putAll(builder.attributes);
    }

    public String getTargetHint() { return targetHint; }
    public String getExpectedTag() { return expectedTag; }
    public String getOriginalLocator() { return originalLocator; }
    public String getScreenName() { return screenName; }
    public String getTargetRsPath() { return targetRsPath; }
    public String getAction() { return action; }
    public String getValue() { return value; }
    public java.io.File getRawScreenshot() { return rawScreenshot; }
    public Map<String, String> getAttributes() { return attributes; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String targetHint;
        private String expectedTag;
        private String originalLocator;
        private String screenName;
        private String targetRsPath;
        private String action;
        private String value;
        private java.io.File rawScreenshot;
        private final Map<String, String> attributes = new HashMap<>();

        public Builder rawScreenshot(java.io.File file) {
            this.rawScreenshot = file;
            return this;
        }

        public Builder targetHint(String hint) {
            this.targetHint = hint;
            return this;
        }

        public Builder expectedTag(String tag) {
            this.expectedTag = tag;
            return this;
        }

        public Builder originalLocator(String locator) {
            this.originalLocator = locator;
            return this;
        }

        public Builder screenName(String screenName) {
            this.screenName = screenName;
            return this;
        }

        public Builder targetRsPath(String rsPath) {
            this.targetRsPath = rsPath;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder attribute(String key, String val) {
            this.attributes.put(key, val);
            return this;
        }

        public HealContext build() {
            return new HealContext(this);
        }
    }
}
