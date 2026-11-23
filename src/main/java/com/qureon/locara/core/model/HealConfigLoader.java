package com.qureon.locara.core.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Loads {@link HealConfig} values following strict precedence:
 * Environment Variables &gt; System Properties &gt; local override file &gt; base properties file.
 */
final class HealConfigLoader {

    private static final String[] LOCAL_OVERRIDE_FILES = {"locara.local.properties", "qureon.local.properties"};

    private HealConfigLoader() {}

    static HealConfig load(File propFile) {
        HealConfig config = new HealConfig();
        Properties props = loadMergedProperties(propFile);

        config.setEnabled(resolveBoolean(props, config.isEnabled(),
                new String[]{"LOCARA_ENABLED", "QUREON_ENABLED", "HEALING_ENABLED"},
                "locara.enabled", "qureon.enabled", "healing.enabled"));

        config.setMinConfidenceThreshold(resolveDouble(props, config.getMinConfidenceThreshold(),
                "locara.threshold", "locara.similarity.threshold", "qureon.threshold", "healing.threshold"));
        config.setWeightText(resolveDouble(props, config.getWeightText(),
                "locara.weight.text", "qureon.weight.text", "healing.weight.text"));
        config.setWeightId(resolveDouble(props, config.getWeightId(),
                "locara.weight.id", "qureon.weight.id", "healing.weight.id"));
        config.setWeightDesc(resolveDouble(props, config.getWeightDesc(),
                "locara.weight.desc", "qureon.weight.desc", "healing.weight.desc"));
        config.setWeightClass(resolveDouble(props, config.getWeightClass(),
                "locara.weight.class", "qureon.weight.class", "healing.weight.class"));
        config.setWeightProximity(resolveDouble(props, config.getWeightProximity(),
                "locara.weight.proximity", "qureon.weight.proximity", "healing.weight.proximity"));

        config.setAutoGenerateRs(resolveBoolean(props, config.isAutoGenerateRs(),
                null, "locara.rs.generate", "qureon.rs.generate", "healing.rs.generate"));
        config.setAllowOverwriteExistingRs(resolveBoolean(props, config.isAllowOverwriteExistingRs(),
                null, "locara.rs.overwrite", "qureon.rs.overwrite", "healing.rs.overwrite"));
        config.setBackupExistingRs(resolveBoolean(props, config.isBackupExistingRs(),
                null, "locara.rs.backup", "qureon.rs.backup", "healing.rs.backup"));
        config.setAuditLedgerEnabled(resolveBoolean(props, config.isAuditLedgerEnabled(),
                null, "locara.audit.enabled", "locara.telemetry.enabled", "qureon.audit.enabled", "healing.audit.enabled"));
        config.setAdaptiveFrameworkWeights(resolveBoolean(props, config.isAdaptiveFrameworkWeights(),
                null, "locara.framework.adaptive", "qureon.framework.adaptive", "healing.framework.adaptive"));

        config.setScrollRecoveryEnabled(resolveBoolean(props, config.isScrollRecoveryEnabled(),
                new String[]{"LOCARA_SCROLL_ENABLED", "QUREON_SCROLL_ENABLED", "HEALING_SCROLL_ENABLED"},
                "locara.scroll.enabled", "qureon.scroll.enabled", "healing.scroll.enabled"));
        config.setMaxScrollAttempts(resolveInt(props, config.getMaxScrollAttempts(),
                null, "locara.scroll.max_attempts", "qureon.scroll.max_attempts", "healing.scroll.max_attempts"));
        config.setManualScrollGuardEnabled(resolveBoolean(props, config.isManualScrollGuardEnabled(),
                new String[]{"LOCARA_MANUAL_SCROLL_GUARD"},
                "locara.scroll.manual_guard.enabled", "qureon.scroll.manual_guard.enabled"));
        config.setManualScrollCooldownMs(resolveLong(props, config.getManualScrollCooldownMs(),
                new String[]{"LOCARA_MANUAL_SCROLL_COOLDOWN_MS"},
                "locara.scroll.manual_cooldown_ms", "qureon.scroll.manual_cooldown_ms"));

        String targetVal = resolveString(props, null,
                "locara.target", "locara.target.profile", "qureon.target", "healing.target");
        if (targetVal != null) {
            config.setAppTarget(AppTarget.fromString(targetVal));
        }

        String projectPath = resolveString(props, null,
                "locara.project.path", "qureon.project.path", "healing.project.path");
        if (projectPath != null && !projectPath.trim().isEmpty()) {
            config.setKatalonProjectPath(projectPath.trim());
        }

        String auditFolder = resolveString(props, null,
                "locara.audit.folder", "qureon.audit.folder", "healing.audit.folder");
        if (auditFolder != null && !auditFolder.trim().isEmpty()) {
            config.setAuditLogFolder(auditFolder.trim());
        }
        return config;
    }

    private static Properties loadMergedProperties(File propFile) {
        Properties props = new Properties();
        if (propFile != null && propFile.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(propFile), StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException ignored) {}
        }
        File overrideDir = (propFile != null && propFile.getParentFile() != null)
                ? propFile.getParentFile()
                : new File(System.getProperty("user.dir"));
        for (String name : LOCAL_OVERRIDE_FILES) {
            File candidate = new File(overrideDir, name);
            if (candidate.exists()) {
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(candidate), StandardCharsets.UTF_8)) {
                    props.load(reader);
                    break;
                } catch (IOException ignored) {}
            }
        }
        return props;
    }

    private static String resolveString(Properties props, String[] envKeys, String... propKeys) {
        if (envKeys != null) {
            for (String envKey : envKeys) {
                String env = System.getenv(envKey);
                if (env != null) return env;
            }
        }
        for (String key : propKeys) {
            String sys = System.getProperty(key);
            if (sys != null) return sys;
        }
        for (String key : propKeys) {
            String prop = props.getProperty(key);
            if (prop != null) return prop;
        }
        return null;
    }

    private static boolean resolveBoolean(Properties props, boolean defVal, String[] envKeys, String... propKeys) {
        String raw = resolveString(props, envKeys, propKeys);
        return raw != null ? Boolean.parseBoolean(raw.trim()) : defVal;
    }

    private static double resolveDouble(Properties props, double defVal, String... propKeys) {
        String raw = resolveString(props, null, propKeys);
        if (raw == null || raw.trim().isEmpty()) return defVal;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return defVal;
        }
    }

    private static int resolveInt(Properties props, int defVal, String[] envKeys, String... propKeys) {
        String raw = resolveString(props, envKeys, propKeys);
        if (raw == null || raw.trim().isEmpty()) return defVal;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defVal;
        }
    }

    private static long resolveLong(Properties props, long defVal, String[] envKeys, String... propKeys) {
        String raw = resolveString(props, envKeys, propKeys);
        if (raw == null || raw.trim().isEmpty()) return defVal;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return defVal;
        }
    }
}
