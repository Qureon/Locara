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

    private boolean enabled = true;

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
                new File(System.getProperty("user.dir"), "qureon.local.properties"),
                new File(System.getProperty("user.dir"), "zero-healing.local.properties"),
                new File(System.getProperty("user.dir"), "zerohealing.local.properties")
        };
        for (File candidate : localCandidates) {
            if (candidate.exists()) {
                try (FileInputStream lfis = new FileInputStream(candidate)) {
                    props.load(lfis);
                    break;
                } catch (IOException ignored) {}
            }
        }

        try {
            config.enabled = Boolean.parseBoolean(props.getProperty("healing.enabled", "true"));
            config.minConfidenceThreshold = Double.parseDouble(props.getProperty("healing.threshold", "0.70"));
            config.weightText = Double.parseDouble(props.getProperty("healing.weight.text", "0.35"));
            config.weightId = Double.parseDouble(props.getProperty("healing.weight.id", "0.25"));
            config.weightDesc = Double.parseDouble(props.getProperty("healing.weight.desc", "0.20"));
            config.weightClass = Double.parseDouble(props.getProperty("healing.weight.class", "0.10"));
            config.weightProximity = Double.parseDouble(props.getProperty("healing.weight.proximity", "0.10"));
            config.autoGenerateRs = Boolean.parseBoolean(props.getProperty("healing.rs.generate", "false"));
            config.allowOverwriteExistingRs = Boolean.parseBoolean(props.getProperty("healing.rs.overwrite", "false"));
            config.backupExistingRs = Boolean.parseBoolean(props.getProperty("healing.rs.backup", "true"));
            config.auditLedgerEnabled = Boolean.parseBoolean(props.getProperty("healing.audit.enabled", "true"));
            config.adaptiveFrameworkWeights = Boolean.parseBoolean(props.getProperty("healing.framework.adaptive", "true"));
            config.katalonProjectPath = props.getProperty("healing.project.path", config.katalonProjectPath);
        } catch (NumberFormatException ignored) {}

        // 3. System properties override (highest precedence)
        String sysEnabled = System.getProperty("locara.enabled",
                System.getProperty("qureon.enabled",
                System.getProperty("zerohealing.enabled", System.getProperty("healing.enabled"))));
        if (sysEnabled != null) {
            config.enabled = Boolean.parseBoolean(sysEnabled);
        }

        // 4. Environment variable override
        String envEnabled = System.getenv("LOCARA_ENABLED");
        if (envEnabled == null) envEnabled = System.getenv("QUREON_ENABLED");
        if (envEnabled == null) envEnabled = System.getenv("ZEROHEALING_ENABLED");
        if (envEnabled != null) {
            config.enabled = Boolean.parseBoolean(envEnabled);
        }

        return config;
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
