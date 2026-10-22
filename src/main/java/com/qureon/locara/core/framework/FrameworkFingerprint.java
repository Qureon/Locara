package com.qureon.locara.core.framework;

/**
 * Result of framework detection containing detected framework, confidence score, and detection rationale.
 */
public class FrameworkFingerprint {
    private final AppFramework framework;
    private final double confidence;
    private final String reason;
    private final double viewGroupRatio;

    public FrameworkFingerprint(AppFramework framework, double confidence, String reason, double viewGroupRatio) {
        this.framework = framework;
        this.confidence = confidence;
        this.reason = reason;
        this.viewGroupRatio = viewGroupRatio;
    }

    public AppFramework getFramework() {
        return framework;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    public double getViewGroupRatio() {
        return viewGroupRatio;
    }

    @Override
    public String toString() {
        return String.format("%s (%.0f%% confidence - %s)", framework.getDisplayName(), confidence * 100.0, reason);
    }
}
