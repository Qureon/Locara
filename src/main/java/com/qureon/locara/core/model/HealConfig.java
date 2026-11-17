package com.qureon.locara.core.model;

import java.io.File;

/**
 * Global configuration parameters for the Locara by Qureon Engine.
 */
public class HealConfig {

    public static final long DEFAULT_MANUAL_SCROLL_COOLDOWN_MS = 15_000L;

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

    private volatile AppTarget appTarget = AppTarget.AUTO;
    private final ThreadLocal<AppTarget> threadAppTarget = new ThreadLocal<>();
    private boolean enabled = true;
    private boolean scrollRecoveryEnabled = true;
    private int maxScrollAttempts = 2;
    private boolean manualScrollGuardEnabled = true;
    private long manualScrollCooldownMs = DEFAULT_MANUAL_SCROLL_COOLDOWN_MS;

    public HealConfig() {}

    public static HealConfig loadFromProperties(File propFile) {
        return HealConfigLoader.load(propFile);
    }

    public boolean isScrollRecoveryEnabled() { return scrollRecoveryEnabled; }
    public void setScrollRecoveryEnabled(boolean val) { this.scrollRecoveryEnabled = val; }

    public int getMaxScrollAttempts() { return maxScrollAttempts; }
    public void setMaxScrollAttempts(int val) { this.maxScrollAttempts = Math.max(0, val); }

    public boolean isManualScrollGuardEnabled() { return manualScrollGuardEnabled; }
    public void setManualScrollGuardEnabled(boolean val) { this.manualScrollGuardEnabled = val; }

    public long getManualScrollCooldownMs() { return manualScrollCooldownMs; }
    public void setManualScrollCooldownMs(long val) { this.manualScrollCooldownMs = Math.max(0L, val); }

    public AppTarget getAppTarget() {
        AppTarget local = threadAppTarget.get();
        return local != null ? local : appTarget;
    }

    public void setAppTarget(AppTarget target) {
        this.appTarget = target != null ? target : AppTarget.AUTO;
        this.threadAppTarget.remove();
    }

    public void setThreadAppTarget(AppTarget target) {
        if (target == null) {
            this.threadAppTarget.remove();
        } else {
            this.threadAppTarget.set(target);
        }
    }

    public void clearThreadAppTarget() {
        this.threadAppTarget.remove();
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
