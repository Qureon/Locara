package com.qureon.locara.core.generator;

import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.UIElementNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synthesizes official Katalon Studio {@code .rs} ({@code MobileElementEntity} / {@code WebElementEntity})
 * XML files directly into the project's Object Repository with path-traversal and anti-tampering protection.
 */
public class KatalonRsSynthesizer {

    private static final Pattern ELEMENT_GUID_PATTERN = Pattern.compile("<elementGuidId>([a-fA-F0-9\\-]+)</elementGuidId>");

    private final DynamicXPathGenerator xpathGenerator = new DynamicXPathGenerator();
    private final RsSemanticNamingService namingService = new RsSemanticNamingService();
    private final RsXmlDocumentRenderer xmlRenderer = new RsXmlDocumentRenderer(xpathGenerator, namingService);

    public enum MobileStrategy {
        ACCESSIBILITY,
        ID,
        ATTRIBUTES,
        XPATH
    }

    public static class PropertyEntry {
        public final String name;
        public final String value;
        public final boolean selected;

        public PropertyEntry(String name, String value, boolean selected) {
            this.name = name;
            this.value = value != null ? value : "";
            this.selected = selected;
        }
    }

    public KatalonRsSynthesizer() {}

    public File synthesizeRsFile(HealCandidate candidate, HealContext context, HealConfig config) throws IOException {
        String rsRelPath = namingService.resolveRsRelativePath(context, candidate, config);
        Path projectRoot = new File(config.getKatalonProjectPath()).toPath().toAbsolutePath().normalize();
        Path normalizedRel = Path.of(rsRelPath.replace("\\", "/"));
        if (normalizedRel.isAbsolute()) {
            throw new IOException("Absolute Object Repository target path is not allowed: " + rsRelPath);
        }
        Path resolvedOriginal = projectRoot.resolve(normalizedRel).normalize();
        if (!resolvedOriginal.startsWith(projectRoot)) {
            throw new IOException("Path traversal blocked outside project root: " + rsRelPath);
        }
        File originalFile = resolvedOriginal.toFile();
        File targetFile = originalFile;

        String existingGuid = extractExistingGuid(originalFile);
        boolean isWeb = detectWebEntity(originalFile, candidate);

        if (originalFile.exists() && !config.isAllowOverwriteExistingRs()) {
            targetFile = new File(originalFile.getParentFile(), originalFile.getName().replace(".rs", ".healed.rs"));
        } else if (originalFile.exists() && config.isBackupExistingRs()) {
            File backupFile = new File(originalFile.getParentFile(), originalFile.getName().replace(".rs", ".backup.rs"));
            Files.copy(originalFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        String entityName = targetFile.getName().replace(".healed.rs", "").replace(".rs", "");
        String xmlContent = isWeb
                ? xmlRenderer.generateWebXmlContent(entityName, candidate, context, existingGuid)
                : xmlRenderer.generateMobileXmlContent(entityName, candidate, context, existingGuid);
        Files.writeString(targetFile.toPath(), xmlContent, StandardCharsets.UTF_8);
        return targetFile;
    }

    public String generateMobileXmlContent(String name, HealCandidate candidate, HealContext context, String existingGuid) {
        return xmlRenderer.generateMobileXmlContent(name, candidate, context, existingGuid);
    }

    public String generateWebXmlContent(String name, HealCandidate candidate, HealContext context, String existingGuid) {
        return xmlRenderer.generateWebXmlContent(name, candidate, context, existingGuid);
    }

    public String generateXmlContent(String name, String xpath, String reason, double confidence) {
        return generateMobileXmlContent(name, new HealCandidate(null, xpath, confidence, reason), null, null);
    }

    public MobileStrategy determineStrategy(UIElementNode node) {
        return determineStrategy(node, null);
    }

    public MobileStrategy determineStrategy(UIElementNode node, String xpath) {
        return xmlRenderer.determineStrategy(node, xpath);
    }

    public static boolean isComplexOrRelationalXPath(UIElementNode node, String xpath) {
        return RsXmlDocumentRenderer.isComplexOrRelationalXPath(node, xpath);
    }

    public String determineLocatorValue(MobileStrategy strategy, UIElementNode node, String fallbackXpath) {
        return xmlRenderer.determineLocatorValue(strategy, node, fallbackXpath);
    }

    public String determineSelectorMethod(MobileStrategy strategy) {
        return xmlRenderer.determineSelectorMethod(strategy);
    }

    public String buildAttributesLocator(UIElementNode node) {
        return xmlRenderer.buildAttributesLocator(node);
    }

    public String buildUiAutomatorLocator(UIElementNode node) {
        return xmlRenderer.buildUiAutomatorLocator(node);
    }

    public List<PropertyEntry> buildPropertyList(UIElementNode node, MobileStrategy strategy, String xpath) {
        return xmlRenderer.buildPropertyList(node, strategy, xpath);
    }

    public String deriveSemanticObjectName(UIElementNode node, String targetHint) {
        return namingService.deriveSemanticObjectName(node, targetHint);
    }

    private boolean detectWebEntity(File originalFile, HealCandidate candidate) {
        if (originalFile.exists()) {
            try {
                if (Files.readString(originalFile.toPath(), StandardCharsets.UTF_8).contains("<WebElementEntity>")) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return candidate != null && candidate.getNode() != null && xpathGenerator.isHtmlNode(candidate.getNode());
    }

    private String extractExistingGuid(File targetFile) {
        if (targetFile != null && targetFile.exists()) {
            try {
                String content = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
                Matcher matcher = ELEMENT_GUID_PATTERN.matcher(content);
                if (matcher.find()) return matcher.group(1);
            } catch (Exception ignored) {}
        }
        return UUID.randomUUID().toString();
    }
}
