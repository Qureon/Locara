package com.qureon.locara.core.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Global configuration parameters for the Locara by Qureon Engine.
 */
public class HealConfig {
    private double minConfidenceThreshold = 0.70;
    private double weightText = 0.35;
    private double weightId = 0.25;
    private double weightDesc = 0.20;
    private double weightClass = 0.10;
    private double weightProximity = 0.10;

    private boolean autoGenerateRs = false;
    private boolean allowOverwriteExistingRs = false;
    private boolean backupExistingRs = true;
    private boolean auditLedgerEnabled = true;
    private boolean adaptiveFrameworkWeights = true;
    private String katalonProjectPath = System.getProperty("user.dir");
    private String defaultRsFolder = "Object Repository";
    private String auditLogFolder = "Include/evidence";

    private AppTarget appTarget = AppTarget.AUTO;
    private final ThreadLocal<AppTarget> threadAppTarget = new ThreadLocal<>();
    private boolean enabled = true;
    private boolean scrollRecoveryEnabled = true;

    public HealConfig() {}

    public static HealConfig loadFromProperties(File propFile) {
        HealConfig config = new HealConfig();
        Properties props = new Properties();

        // 1. Load base properties
        if (propFile != null && propFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propFile)) {
                props.load(fis);
            } catch (IOException ignored) {}
        }

        // 2. Load local properties override if exists (ignored by Git)
        File[] localCandidates = new File[] {
                new File(System.getProperty("user.dir"), "locara.local.properties"),
                new File(System.getProperty("user.dir"), "qureon.local.properties")
        };
        for (File candidate : localCandidates) {
            if (candidate.exists()) {
                try (FileInputStream lfis = new FileInputStream(candidate)) {
                    props.load(lfis);
                    break;
                } catch (IOException ignored) {}
            }
        }

        String propEnabled = props.getProperty("locara.enabled",
                props.getProperty("qureon.enabled",
                props.getProperty("healing.enabled", "true")));
        config.enabled = Boolean.parseBoolean(propEnabled);

        config.minConfidenceThreshold = parseDoubleSafe(props.getProperty("locara.threshold",
                props.getProperty("locara.similarity.threshold",
                props.getProperty("qureon.threshold",
                props.getProperty("healing.threshold")))), 0.70);
        config.weightText = parseDoubleSafe(props.getProperty("locara.weight.text",
                props.getProperty("qureon.weight.text",
                props.getProperty("healing.weight.text"))), 0.35);
        config.weightId = parseDoubleSafe(props.getProperty("locara.weight.id",
                props.getProperty("qureon.weight.id",
                props.getProperty("healing.weight.id"))), 0.25);
        config.weightDesc = parseDoubleSafe(props.getProperty("locara.weight.desc",
                props.getProperty("qureon.weight.desc",
                props.getProperty("healing.weight.desc"))), 0.20);
        config.weightClass = parseDoubleSafe(props.getProperty("locara.weight.class",
                props.getProperty("qureon.weight.class",
                props.getProperty("healing.weight.class"))), 0.10);
        config.weightProximity = parseDoubleSafe(props.getProperty("locara.weight.proximity",
                props.getProperty("qureon.weight.proximity",
                props.getProperty("healing.weight.proximity"))), 0.10);

        config.autoGenerateRs = Boolean.parseBoolean(props.getProperty("locara.rs.generate",
                props.getProperty("qureon.rs.generate",
                props.getProperty("healing.rs.generate", "false"))));
        config.allowOverwriteExistingRs = Boolean.parseBoolean(props.getProperty("locara.rs.overwrite",
                props.getProperty("qureon.rs.overwrite",
                props.getProperty("healing.rs.overwrite", "false"))));
        config.backupExistingRs = Boolean.parseBoolean(props.getProperty("locara.rs.backup",
                props.getProperty("qureon.rs.backup",
                props.getProperty("healing.rs.backup", "true"))));
        config.auditLedgerEnabled = Boolean.parseBoolean(props.getProperty("locara.audit.enabled",
                props.getProperty("locara.telemetry.enabled",
                props.getProperty("qureon.audit.enabled",
                props.getProperty("healing.audit.enabled", "true")))));
        config.adaptiveFrameworkWeights = Boolean.parseBoolean(props.getProperty("locara.framework.adaptive",
                props.getProperty("qureon.framework.adaptive",
                props.getProperty("healing.framework.adaptive", "true"))));
        String scrollProp = props.getProperty("locara.scroll.enabled",
                props.getProperty("qureon.scroll.enabled",
                props.getProperty("healing.scroll.enabled", "true")));
        config.scrollRecoveryEnabled = Boolean.parseBoolean(scrollProp);
        String targetProp = props.getProperty("locara.target",
                props.getProperty("locara.target.profile",
                props.getProperty("qureon.target",
                props.getProperty("healing.target", "AUTO"))));
        config.setAppTarget(AppTarget.fromString(targetProp));
        config.katalonProjectPath = props.getProperty("locara.project.path",
                props.getProperty("qureon.project.path",
                props.getProperty("healing.project.path", config.katalonProjectPath)));

        // 3. System properties override (highest precedence)
        String sysEnabled = System.getProperty("locara.enabled",
                System.getProperty("qureon.enabled",
                System.getProperty("healing.enabled")));
        if (sysEnabled != null) {
            config.enabled = Boolean.parseBoolean(sysEnabled);
        }
        String sysScroll = System.getProperty("locara.scroll.enabled",
                System.getProperty("qureon.scroll.enabled",
                System.getProperty("healing.scroll.enabled")));
        if (sysScroll != null) {
            config.scrollRecoveryEnabled = Boolean.parseBoolean(sysScroll);
        }
        String sysTarget = System.getProperty("locara.target",
                System.getProperty("locara.target.profile",
                System.getProperty("qureon.target",
                System.getProperty("healing.target"))));
        if (sysTarget != null) {
            config.setAppTarget(AppTarget.fromString(sysTarget));
        }

        // 4. Environment variable override
        String envEnabled = System.getenv("LOCARA_ENABLED");
        if (envEnabled == null) envEnabled = System.getenv("QUREON_ENABLED");
        if (envEnabled == null) envEnabled = System.getenv("HEALING_ENABLED");
        if (envEnabled != null) {
            config.enabled = Boolean.parseBoolean(envEnabled);
        }
        String envScroll = System.getenv("LOCARA_SCROLL_ENABLED");
        if (envScroll == null) envScroll = System.getenv("QUREON_SCROLL_ENABLED");
        if (envScroll == null) envScroll = System.getenv("HEALING_SCROLL_ENABLED");
        if (envScroll != null) {
            config.scrollRecoveryEnabled = Boolean.parseBoolean(envScroll);
        }

        return config;
    }

    private static double parseDoubleSafe(String val, double defaultVal) {
        if (val == null || val.trim().isEmpty()) return defaultVal;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public boolean isScrollRecoveryEnabled() { return scrollRecoveryEnabled; }
    public void setScrollRecoveryEnabled(boolean val) { this.scrollRecoveryEnabled = val; }

    public AppTarget getAppTarget() {
        AppTarget local = threadAppTarget.get();
        return local != null ? local : appTarget;
    }

    public void setAppTarget(AppTarget target) {
        AppTarget resolved = target != null ? target : AppTarget.AUTO;
        this.appTarget = resolved;
        this.threadAppTarget.set(resolved);
    }

    public void setThreadAppTarget(AppTarget target) {
        if (target == null) {
            this.threadAppTarget.remove();
        } else {
            this.threadAppTarget.set(target);
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean val) { this.enabled = val; }

    public boolean isAllowOverwriteExistingRs() { return allowOverwriteExistingRs; }
    public void setAllowOverwriteExistingRs(boolean val) { this.allowOverwriteExistingRs = val; }

    public boolean isAdaptiveFrameworkWeights() { return adaptiveFrameworkWeights; }
    public void setAdaptiveFrameworkWeights(boolean val) { this.adaptiveFrameworkWeights = val; }

    public double getMinConfidenceThreshold() { return minConfidenceThreshold; }
    public void setMinConfidenceThreshold(double val) { this.minConfidenceThreshold = val; }

    public double getWeightText() { return weightText; }
    public void setWeightText(double val) { this.weightText = val; }

    public double getWeightId() { return weightId; }
    public void setWeightId(double val) { this.weightId = val; }

    public double getWeightDesc() { return weightDesc; }
    public void setWeightDesc(double val) { this.weightDesc = val; }

    public double getWeightClass() { return weightClass; }
    public void setWeightClass(double val) { this.weightClass = val; }

    public double getWeightProximity() { return weightProximity; }
    public void setWeightProximity(double val) { this.weightProximity = val; }

    public boolean isAutoGenerateRs() { return autoGenerateRs; }
    public void setAutoGenerateRs(boolean val) { this.autoGenerateRs = val; }

    public boolean isBackupExistingRs() { return backupExistingRs; }
    public void setBackupExistingRs(boolean val) { this.backupExistingRs = val; }

    public boolean isAuditLedgerEnabled() { return auditLedgerEnabled; }
    public void setAuditLedgerEnabled(boolean val) { this.auditLedgerEnabled = val; }

    public String getKatalonProjectPath() { return katalonProjectPath; }
    public void setKatalonProjectPath(String val) { this.katalonProjectPath = val; }

    public String getDefaultRsFolder() { return defaultRsFolder; }
    public void setDefaultRsFolder(String val) { this.defaultRsFolder = val; }

    public String getAuditLogFolder() { return auditLogFolder; }
    public void setAuditLogFolder(String val) { this.auditLogFolder = val; }
}
