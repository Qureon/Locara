package com.qureon.locara.core.ledger;

import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Audit ledger recording all self-healing events to JSON and Markdown reports.
 */
public class HealingAuditLedger {
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Object FILE_LOCK = new Object();

    public HealingAuditLedger() {}

    public void recordEvent(HealContext context,
                            HealCandidate candidate,
                            File generatedRs,
                            String status,
                            HealConfig config) {
        recordEvent(context, candidate, generatedRs, status, config, null);
    }

    public void recordEvent(HealContext context,
                            HealCandidate candidate,
                            File generatedRs,
                            String status,
                            HealConfig config,
                            com.qureon.locara.core.framework.FrameworkFingerprint fingerprint) {
        if (config == null || !config.isAuditLedgerEnabled()) return;
        if (context == null) {
            context = new HealContext.Builder().build();
        }

        synchronized (FILE_LOCK) {
            File projectDir = new File(config.getKatalonProjectPath());
            File evidenceDir = new File(projectDir, config.getAuditLogFolder());
            if (!evidenceDir.exists()) {
                evidenceDir.mkdirs();
            }

        File jsonLogFile = new File(evidenceDir, "locara_audit.json");
        File mdLogFile = new File(evidenceDir, "locara_audit.md");
        File legacyJsonFile = new File(evidenceDir, "healing_audit.json");
        File legacyMdFile = new File(evidenceDir, "healing_audit.md");

        String now = LocalDateTime.now().format(ISO_FMT);
        String rsPath = generatedRs != null ? generatedRs.getPath() : "";
        String healedXPath = candidate != null ? candidate.getSuggestedXPath() : "";
        double confidence = candidate != null ? candidate.getConfidenceScore() : 0.0;
        String reason = candidate != null ? candidate.getHealReason() : "None";
        String frameworkTag = fingerprint != null ? fingerprint.getFramework().getShortTag() : "Generic";
        String action = context.getAction() != null && !context.getAction().isEmpty() ? context.getAction() : "TAP";
        String eventId = java.util.UUID.randomUUID().toString().substring(0, 8);

        String threadName = Thread.currentThread().getName();
        String intentionTier = context.getIntentionTier() != null ? context.getIntentionTier().name() : "INTERACTION";
        String intentionGroup = context.getIntentionGroup() != null ? context.getIntentionGroup() : "";
        String tags = context.getExecutionTags() != null ? context.getExecutionTags() : "";

        // Append to JSON Lines with Locale.US and full schema keys for dashboard
        String jsonEntry = String.format(
                java.util.Locale.US,
                "{\"id\":\"%s\",\"timestamp\":\"%s\",\"screen\":\"%s\",\"action\":\"%s\",\"intentionTier\":\"%s\",\"intentionGroup\":\"%s\",\"tags\":\"%s\",\"hint\":\"%s\",\"targetHint\":\"%s\",\"origLocator\":\"%s\",\"status\":\"%s\",\"confidence\":%.4f,\"framework\":\"%s\",\"healedXPath\":\"%s\",\"healedLocator\":\"%s\",\"rsFile\":\"%s\",\"generatedRs\":\"%s\",\"reason\":\"%s\",\"thread\":\"%s\"}\n",
                eventId,
                now,
                escapeJson(context.getScreenName()),
                escapeJson(action),
                escapeJson(intentionTier),
                escapeJson(intentionGroup),
                escapeJson(tags),
                escapeJson(context.getTargetHint()),
                escapeJson(context.getTargetHint()),
                escapeJson(context.getOriginalLocator()),
                escapeJson(status),
                confidence,
                escapeJson(frameworkTag),
                escapeJson(healedXPath),
                escapeJson(healedXPath),
                escapeJson(rsPath),
                escapeJson(rsPath),
                escapeJson(reason),
                escapeJson(threadName)
        );

        try {
            Files.writeString(
                    jsonLogFile.toPath(),
                    jsonEntry,
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
            Files.writeString(
                    legacyJsonFile.toPath(),
                    jsonEntry,
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {}

        // Append to Markdown table
        boolean isNewMd = !mdLogFile.exists();
        StringBuilder mdBuilder = new StringBuilder();
        if (isNewMd) {
            mdBuilder.append("# 🛡️ Locara by Qureon Audit Ledger\n\n");
            mdBuilder.append("| Timestamp | Screen | Action | Hint | Status | Confidence | Healed XPath | Generated .rs |\n");
            mdBuilder.append("|---|---|---|---|---|---|---|---|\n");
        }
        mdBuilder.append(String.format(
                java.util.Locale.US,
                "| %s | %s | %s | %s | **%s** | %.1f%% | `%s` | `%s` |\n",
                now,
                escapeMd(context.getScreenName()),
                escapeMd(action),
                escapeMd(context.getTargetHint()),
                escapeMd(status),
                confidence * 100.0,
                escapeMd(healedXPath),
                escapeMd(rsPath)
        ));

        try {
            Files.writeString(
                    mdLogFile.toPath(),
                    mdBuilder.toString(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
            Files.writeString(
                    legacyMdFile.toPath(),
                    mdBuilder.toString(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {}
        }
    }

    private String escapeMd(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
