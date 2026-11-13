package com.qureon.locara.core.ledger;

import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Audit ledger recording all self-healing events to JSON and Markdown reports
 * with mandatory PII masking on persisted strings.
 */
public class HealingAuditLedger {
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Object FILE_LOCK = new Object();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern SECRET_KV_PATTERN = Pattern.compile("(?i)\\b(m?pin|password|passwd|pwd|otp|cvv|secret|token|bearer)\\b\\s*([:=])\\s*(['\"]?)(?!(?:true|false)\\b)([^'\"\\s,;]{2,})\\3");
    private static final Pattern LONG_DIGIT_PATTERN = Pattern.compile("(?<!\\d)\\d{10,16}(?!\\d)");
    private static final Pattern SPACED_CARD_PATTERN = Pattern.compile("(?<!\\d)\\d{4}(?:[\\s-]\\d{4}){2,3}(?!\\d)");

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
            if (!evidenceDir.exists() && !evidenceDir.mkdirs()) {
                System.err.println("[Locara by Qureon] Warning: failed to create audit evidence directory: " + evidenceDir.getAbsolutePath());
                return;
            }

            File jsonLogFile = new File(evidenceDir, "locara_audit.json");
            File mdLogFile = new File(evidenceDir, "locara_audit.md");

            String now = LocalDateTime.now().format(ISO_FMT);
            String rsPath = generatedRs != null ? generatedRs.getPath() : "";
            String healedLocator = candidate != null ? maskPii(candidate.getSuggestedXPath()) : "";
            double confidence = candidate != null ? candidate.getConfidenceScore() : 0.0;
            String reason = candidate != null ? candidate.getHealReason() : "None";
            String frameworkTag = fingerprint != null ? fingerprint.getFramework().getShortTag() : "Generic";
            String action = context.getAction() != null && !context.getAction().isEmpty() ? context.getAction() : "TAP";
            String eventId = UUID.randomUUID().toString().substring(0, 8);

            String threadName = Thread.currentThread().getName();
            String intentionTier = context.getIntentionTier() != null ? context.getIntentionTier().name() : "INTERACTION";
            String intentionGroup = context.getIntentionGroup() != null ? context.getIntentionGroup() : "";
            String maskedTags = maskPii(context.getExecutionTags());
            String maskedScreen = maskPii(context.getScreenName());
            String maskedHint = maskPii(context.getTargetHint());
            String maskedOrig = maskPii(context.getOriginalLocator());
            String maskedRsPath = maskPii(rsPath);

            String jsonEntry = String.format(
                    Locale.US,
                    "{\"id\":\"%s\",\"timestamp\":\"%s\",\"screen\":\"%s\",\"action\":\"%s\",\"intentionTier\":\"%s\",\"intentionGroup\":\"%s\",\"tags\":\"%s\",\"targetHint\":\"%s\",\"origLocator\":\"%s\",\"status\":\"%s\",\"confidence\":%.4f,\"framework\":\"%s\",\"healedLocator\":\"%s\",\"generatedRs\":\"%s\",\"reason\":\"%s\",\"thread\":\"%s\"}\n",
                    eventId,
                    now,
                    escapeJson(maskedScreen),
                    escapeJson(action),
                    escapeJson(intentionTier),
                    escapeJson(intentionGroup),
                    escapeJson(maskedTags),
                    escapeJson(maskedHint),
                    escapeJson(maskedOrig),
                    escapeJson(status),
                    confidence,
                    escapeJson(frameworkTag),
                    escapeJson(healedLocator),
                    escapeJson(maskedRsPath),
                    escapeJson(reason),
                    escapeJson(threadName)
            );

            try {
                Files.writeString(
                        jsonLogFile.toPath(),
                        jsonEntry,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException e) {
                System.err.println("[Locara by Qureon] Failed to write JSON audit ledger: " + e.getMessage());
            }

            boolean isNewMd = !mdLogFile.exists();
            StringBuilder mdBuilder = new StringBuilder();
            if (isNewMd) {
                mdBuilder.append("# 🛡️ Locara by Qureon Audit Ledger\n\n");
                mdBuilder.append("| Timestamp | Screen | Action | Hint | Status | Confidence | Healed XPath | Generated .rs |\n");
                mdBuilder.append("|---|---|---|---|---|---|---|---|\n");
            }
            mdBuilder.append(String.format(
                    Locale.US,
                    "| %s | %s | %s | %s | **%s** | %.1f%% | `%s` | `%s` |\n",
                    now,
                    escapeMd(maskedScreen),
                    escapeMd(action),
                    escapeMd(maskedHint),
                    escapeMd(status),
                    confidence * 100.0,
                    escapeMd(healedLocator),
                    escapeMd(maskedRsPath)
            ));

            try {
                Files.writeString(
                        mdLogFile.toPath(),
                        mdBuilder.toString(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException e) {
                System.err.println("[Locara by Qureon] Failed to write Markdown audit ledger: " + e.getMessage());
            }
        }
    }

    /**
     * Masks common PII & credentials (emails, explicit PIN/password/OTP values, 10-16 digit accounts/phones,
     * and spaced card/account blocks) before persisting audit logs to disk.
     */
    public static String maskPii(String input) {
        if (input == null || input.isEmpty()) return "";
        String masked = EMAIL_PATTERN.matcher(input).replaceAll("[MASKED_EMAIL]");

        Matcher kvMatcher = SECRET_KV_PATTERN.matcher(masked);
        StringBuilder kvSb = new StringBuilder();
        while (kvMatcher.find()) {
            String replacement = Matcher.quoteReplacement(
                    kvMatcher.group(1) + kvMatcher.group(2) + kvMatcher.group(3) + "****" + kvMatcher.group(3)
            );
            kvMatcher.appendReplacement(kvSb, replacement);
        }
        kvMatcher.appendTail(kvSb);
        masked = kvSb.toString();

        Matcher spacedMatcher = SPACED_CARD_PATTERN.matcher(masked);
        StringBuilder spacedSb = new StringBuilder();
        while (spacedMatcher.find()) {
            String digitsOnly = spacedMatcher.group().replaceAll("\\D", "");
            String tail = digitsOnly.length() >= 4 ? digitsOnly.substring(digitsOnly.length() - 4) : "";
            spacedMatcher.appendReplacement(spacedSb, Matcher.quoteReplacement("****" + tail));
        }
        spacedMatcher.appendTail(spacedSb);
        masked = spacedSb.toString();

        Matcher digitMatcher = LONG_DIGIT_PATTERN.matcher(masked);
        StringBuilder digitSb = new StringBuilder();
        while (digitMatcher.find()) {
            String num = digitMatcher.group();
            String tail = num.substring(num.length() - 4);
            digitMatcher.appendReplacement(digitSb, Matcher.quoteReplacement("******" + tail));
        }
        digitMatcher.appendTail(digitSb);
        return digitSb.toString();
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
