package com.qureon.locara.core.framework;

/**
 * Adaptive vector weights tailored specifically to the structural characteristics of each mobile framework.
 */
public class FrameworkTuningProfile {
    private final AppFramework framework;
    private final double weightText;
    private final double weightId;
    private final double weightDesc;
    private final double weightClass;
    private final double weightProximity;

    public FrameworkTuningProfile(AppFramework framework, double weightText, double weightId,
                                  double weightDesc, double weightClass, double weightProximity) {
        this.framework = framework;
        this.weightText = weightText;
        this.weightId = weightId;
        this.weightDesc = weightDesc;
        this.weightClass = weightClass;
        this.weightProximity = weightProximity;
    }

    public static FrameworkTuningProfile getProfileFor(AppFramework framework) {
        if (framework == null) {
            return getDefaultProfile();
        }

        switch (framework) {
            case REACT_NATIVE:
                // React Native (Tailored for M-Smile & Modern RN apps without testIDs):
                // Elevate visible text and structural proximity, reduce reliance on missing testID/accessibilityLabel
                return new FrameworkTuningProfile(AppFramework.REACT_NATIVE, 0.55, 0.08, 0.07, 0.15, 0.15);

            case FLUTTER:
                // Flutter: Primary content is exposed almost exclusively through accessibility content-desc
                return new FrameworkTuningProfile(AppFramework.FLUTTER, 0.20, 0.05, 0.60, 0.05, 0.10);

            case JETPACK_COMPOSE:
                // Jetpack Compose: Text and semantics contentDescription are primary
                return new FrameworkTuningProfile(AppFramework.JETPACK_COMPOSE, 0.35, 0.15, 0.35, 0.05, 0.10);

            case NATIVE_ANDROID:
            default:
                // Classic Native Kotlin/Java XML: Balanced weights for resource-ids and content-desc
                return new FrameworkTuningProfile(AppFramework.NATIVE_ANDROID, 0.35, 0.25, 0.20, 0.10, 0.10);
        }
    }

    public static FrameworkTuningProfile getDefaultProfile() {
        return new FrameworkTuningProfile(AppFramework.NATIVE_ANDROID, 0.35, 0.25, 0.20, 0.10, 0.10);
    }

    public AppFramework getFramework() {
        return framework;
    }

    public double getWeightText() {
        return weightText;
    }

    public double getWeightId() {
        return weightId;
    }

    public double getWeightDesc() {
        return weightDesc;
    }

    public double getWeightClass() {
        return weightClass;
    }

    public double getWeightProximity() {
        return weightProximity;
    }
}
