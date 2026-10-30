package com.qureon.locara.core.sanitizer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 🧼 LocaraOrSanitizer — Object Repository Hygiene & Relative XPath Converter.
 *
 * Scans Katalon Studio .rs files, detects fragile absolute hierarchy XPaths (//hierarchy/...),
 * and synthesizes resilient relative XPaths based on semantic text, content-desc, or clean IDs.
 */
public class LocaraOrSanitizer {

    private static final Pattern PROP_PATTERN = Pattern.compile(
            "<webElementProperties>.*?<name>([^<]+)</name>.*?<value>([^<]*)</value>.*?</webElementProperties>",
            Pattern.DOTALL
    );

    private static final Pattern LOCATOR_PATTERN = Pattern.compile("<locator>([^<]*)</locator>");
    private static final Pattern STRATEGY_PATTERN = Pattern.compile("<locatorStrategy>([^<]*)</locatorStrategy>");

    public static class SanitizeResult {
        private final File file;
        private final boolean wasBrittle;
        private final boolean sanitized;
        private final String originalLocator;
        private final String newLocator;
        private final String reason;

        public SanitizeResult(File file, boolean wasBrittle, boolean sanitized, String originalLocator, String newLocator, String reason) {
            this.file = file;
            this.wasBrittle = wasBrittle;
            this.sanitized = sanitized;
            this.originalLocator = originalLocator;
            this.newLocator = newLocator;
            this.reason = reason;
        }

        public File getFile() { return file; }
        public boolean wasBrittle() { return wasBrittle; }
        public boolean isSanitized() { return sanitized; }
        public String getOriginalLocator() { return originalLocator; }
        public String getNewLocator() { return newLocator; }
        public String getReason() { return reason; }
    }

    public static class SanitizeSummary {
        private final int totalScanned;
        private final int brittleFound;
        private final int totalSanitized;
        private final List<SanitizeResult> results;

        public SanitizeSummary(int totalScanned, int brittleFound, int totalSanitized, List<SanitizeResult> results) {
            this.totalScanned = totalScanned;
            this.brittleFound = brittleFound;
            this.totalSanitized = totalSanitized;
            this.results = results;
        }

        public int getTotalScanned() { return totalScanned; }
        public int getBrittleFound() { return brittleFound; }
        public int getTotalSanitized() { return totalSanitized; }
        public List<SanitizeResult> getResults() { return results; }
    }

    /**
     * Scans a file or directory recursively and sanitizes brittle .rs files if writeChanges is true.
     */
    public SanitizeSummary scanAndSanitize(File root, boolean writeChanges, boolean createBackup) throws IOException {
        List<File> rsFiles = new ArrayList<>();
        collectRsFiles(root, rsFiles);

        List<SanitizeResult> results = new ArrayList<>();
        int brittleCount = 0;
        int sanitizedCount = 0;

        for (File rsFile : rsFiles) {
            SanitizeResult res = processSingleFile(rsFile, writeChanges, createBackup);
            results.add(res);
            if (res.wasBrittle()) brittleCount++;
            if (res.isSanitized()) sanitizedCount++;
        }

        return new SanitizeSummary(rsFiles.size(), brittleCount, sanitizedCount, results);
    }

    public SanitizeResult processSingleFile(File rsFile, boolean writeChanges, boolean createBackup) throws IOException {
        String content = Files.readString(rsFile.toPath(), StandardCharsets.UTF_8);

        boolean isBrittle = content.contains("//hierarchy") || content.contains("<name>xpath</name>\n      <type>Main</type>\n      <value>//hierarchy");

        if (!isBrittle) {
            return new SanitizeResult(rsFile, false, false, null, null, "Already resilient");
        }

        Map<String, String> properties = extractProperties(content);
        String text = properties.get("text");
        String contentDesc = properties.get("content-desc");
        String resourceId = properties.get("resource-id");

        String optimalXPath = null;
        String reason = null;

        if (contentDesc != null && !contentDesc.trim().isEmpty()) {
            optimalXPath = "//*[@content-desc = '" + escapeXPath(contentDesc.trim()) + "']";
            reason = "Synthesized from content-desc";
        } else if (text != null && !text.trim().isEmpty()) {
            String cleanText = text.trim();
            optimalXPath = "//*[contains(@text, '" + escapeXPath(cleanText) + "') or contains(@content-desc, '" + escapeXPath(cleanText) + "')]";
            reason = "Synthesized from text attribute";
        } else if (resourceId != null && !resourceId.trim().isEmpty() && !resourceId.contains(":id/")) {
            optimalXPath = "//*[@resource-id = '" + escapeXPath(resourceId.trim()) + "']";
            reason = "Synthesized from clean resource-id";
        } else {
            return new SanitizeResult(rsFile, true, false, null, null, "Brittle hierarchy, but lacks text/content-desc properties to safely synthesize relative XPath");
        }

        if (!writeChanges) {
            return new SanitizeResult(rsFile, true, false, "hierarchy", optimalXPath, reason + " (Dry-run)");
        }

        if (createBackup) {
            File backupFile = new File(rsFile.getAbsolutePath() + ".bak");
            Files.writeString(backupFile.toPath(), content, StandardCharsets.UTF_8);
        }

        String updated = updateRsContent(content, optimalXPath);
        Files.writeString(rsFile.toPath(), updated, StandardCharsets.UTF_8);

        return new SanitizeResult(rsFile, true, true, "hierarchy", optimalXPath, reason);
    }

    private static String updateRsContent(String content, String newXPath) {
        String result = content;

        if (LOCATOR_PATTERN.matcher(result).find()) {
            result = LOCATOR_PATTERN.matcher(result).replaceFirst("<locator>" + Matcher.quoteReplacement(newXPath) + "</locator>");
        } else {
            int closeTag = result.lastIndexOf("</MobileElementEntity>");
            if (closeTag != -1) {
                result = result.substring(0, closeTag) + "<locator>" + newXPath + "</locator>\n</MobileElementEntity>";
            }
        }

        if (STRATEGY_PATTERN.matcher(result).find()) {
            result = STRATEGY_PATTERN.matcher(result).replaceFirst("<locatorStrategy>XPATH</locatorStrategy>");
        }

        return result;
    }

    private static Map<String, String> extractProperties(String content) {
        Map<String, String> map = new HashMap<>();
        Matcher m = PROP_PATTERN.matcher(content);
        while (m.find()) {
            map.put(m.group(1).trim(), m.group(2));
        }
        return map;
    }

    private static String escapeXPath(String val) {
        return val.replace("'", "\\'");
    }

    private void collectRsFiles(File dir, List<File> accumulator) {
        if (dir == null || !dir.exists()) return;
        if (dir.isFile() && dir.getName().endsWith(".rs")) {
            accumulator.add(dir);
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectRsFiles(child, accumulator);
            } else if (child.getName().endsWith(".rs")) {
                accumulator.add(child);
            }
        }
    }
}
