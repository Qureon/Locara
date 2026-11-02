package com.qureon.locara.core.generator;

import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.UIElementNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Synthesizes official Katalon Studio .rs (MobileElementEntity) XML files
 * directly into the project's Object Repository structure.
 * Supports dynamic LocatorStrategy (ACCESSIBILITY, ATTRIBUTES, ID, XPATH)
 * and rich Object Properties mirroring Katalon Mobile Spy.
 */
public class KatalonRsSynthesizer {

    private static final Set<String> KNOWN_PROPS = new HashSet<>(Arrays.asList(
            "class", "index", "text", "package", "content-desc", "checkable", "checked",
            "clickable", "enabled", "focusable", "focused", "scrollable", "long-clickable",
            "password", "selected", "bounds", "displayed", "xpath", "resource-id", "tag"
    ));

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

    /**
     * Synthesizes and writes the .rs file to disk.
     * @return File pointer to the generated .rs file.
     */
    public File synthesizeRsFile(HealCandidate candidate, HealContext context, HealConfig config) throws IOException {
        String rsRelPath = resolveRsRelativePath(context, candidate);
        File projectDir = new File(config.getKatalonProjectPath());
        File targetFile = new File(projectDir, rsRelPath);

        // Anti-Tampering Protection: If the file already exists on disk and overwrite is NOT explicitly allowed,
        // write to a safe quarantine file (.healed.rs) to prevent destroying original spied locators!
        if (targetFile.exists() && !config.isAllowOverwriteExistingRs()) {
            targetFile = new File(targetFile.getParentFile(), targetFile.getName().replace(".rs", ".healed.rs"));
        } else if (targetFile.exists() && config.isBackupExistingRs()) {
            File backupFile = new File(targetFile.getParentFile(), targetFile.getName().replace(".rs", ".backup.rs"));
            Files.copy(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // Ensure parent directories exist
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        String entityName = targetFile.getName().replace(".healed.rs", "").replace(".rs", "");
        String existingGuid = extractExistingGuid(targetFile);

        boolean isWeb = false;
        if (targetFile.exists()) {
            try {
                String existing = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
                if (existing.contains("<WebElementEntity>")) {
                    isWeb = true;
                }
            } catch (Exception ignored) {}
        }

        String xmlContent = isWeb ?
                generateWebXmlContent(entityName, candidate, context, existingGuid) :
                generateMobileXmlContent(entityName, candidate, context, existingGuid);

        Files.writeString(targetFile.toPath(), xmlContent, StandardCharsets.UTF_8);

        return targetFile;
    }

    /**
     * Generates genuine Katalon Studio MobileElementEntity XML.
     */
    public String generateMobileXmlContent(String name, HealCandidate candidate, HealContext context, String existingGuid) {
        UIElementNode node = candidate != null ? candidate.getNode() : null;
        MobileStrategy strategy = determineStrategy(node);

        String entityGuid = (existingGuid != null && !existingGuid.isEmpty()) ? existingGuid : UUID.randomUUID().toString();
        String reason = candidate != null ? candidate.getHealReason() : "Self-Healing";
        double confidence = candidate != null ? candidate.getConfidenceScore() : 1.0;
        String xpath = candidate != null ? candidate.getSuggestedXPath() : "";

        String locatorValue = determineLocatorValue(strategy, node, xpath);
        String selectorMethod = determineSelectorMethod(strategy);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MobileElementEntity>\n");
        sb.append("   <description>Auto-generated by Locara by Qureon Engine (")
                .append(reason).append(" - Confidence: ")
                .append(String.format(java.util.Locale.US, "%.1f%%", confidence * 100.0))
                .append(")</description>\n");
        sb.append("   <name>").append(sanitizeXml(name)).append("</name>\n");
        sb.append("   <tag></tag>\n");
        sb.append("   <elementGuidId>").append(entityGuid).append("</elementGuidId>\n");
        sb.append("   <selectorMethod>").append(selectorMethod).append("</selectorMethod>\n");
        sb.append("   <smartLocatorEnabled>false</smartLocatorEnabled>\n");
        sb.append("   <useRalativeImagePath>false</useRalativeImagePath>\n");

        // Rich Object Properties Table (<webElementProperties>)
        List<PropertyEntry> properties = buildPropertyList(node, strategy, xpath);
        for (PropertyEntry prop : properties) {
            sb.append("   <webElementProperties>\n");
            sb.append("      <isSelected>").append(prop.selected).append("</isSelected>\n");
            sb.append("      <matchCondition>equals</matchCondition>\n");
            sb.append("      <name>").append(sanitizeXml(prop.name)).append("</name>\n");
            sb.append("      <type>Main</type>\n");
            sb.append("      <value>").append(sanitizeXml(prop.value)).append("</value>\n");
            sb.append("      <webElementGuid>").append(UUID.randomUUID().toString()).append("</webElementGuid>\n");
            sb.append("   </webElementProperties>\n");
        }

        // Locator
        sb.append("   <locator>").append(sanitizeXml(locatorValue)).append("</locator>\n");

        // Full Locator Collection
        sb.append("   <locatorCollection>\n");
        appendCollectionEntry(sb, "ACCESSIBILITY", (node != null && node.hasContentDesc()) ? node.getContentDesc() : "");
        appendCollectionEntry(sb, "XPATH", xpath);
        appendCollectionEntry(sb, "ATTRIBUTES", buildAttributesLocator(node));
        appendCollectionEntry(sb, "CLASS_NAME", node != null ? node.getClassName() : "");
        appendCollectionEntry(sb, "ANDROID_UI_AUTOMATOR", buildUiAutomatorLocator(node));
        appendCollectionEntry(sb, "ID", (node != null && node.hasResourceId()) ? node.getResourceId() : "");
        appendCollectionEntry(sb, "IMAGE", "");
        appendCollectionEntry(sb, "CUSTOM", "");
        appendCollectionEntry(sb, "ANDROID_VIEWTAG", "");
        appendCollectionEntry(sb, "NAME", "");
        appendCollectionEntry(sb, "IOS_PREDICATE_STRING", "");
        appendCollectionEntry(sb, "IOS_CLASS_CHAIN", "");
        sb.append("   </locatorCollection>\n");

        sb.append("   <locatorStrategy>").append(strategy.name()).append("</locatorStrategy>\n");
        sb.append("   <platform>ANDROID</platform>\n");
        sb.append("</MobileElementEntity>\n");

        return sb.toString();
    }

    /**
     * Generates genuine Katalon Studio WebElementEntity XML for Hybrid WebViews and Browser testing.
     */
    public String generateWebXmlContent(String name, HealCandidate candidate, HealContext context, String existingGuid) {
        String entityGuid = (existingGuid != null && !existingGuid.isEmpty()) ? existingGuid : UUID.randomUUID().toString();
        String reason = candidate != null ? candidate.getHealReason() : "Self-Healing";
        double confidence = candidate != null ? candidate.getConfidenceScore() : 1.0;
        String xpath = candidate != null ? candidate.getSuggestedXPath() : "";

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<WebElementEntity>\n");
        sb.append("   <description>Auto-generated by Locara by Qureon Engine (")
                .append(reason).append(" - Confidence: ")
                .append(String.format(java.util.Locale.US, "%.1f%%", confidence * 100.0))
                .append(")</description>\n");
        sb.append("   <name>").append(sanitizeXml(name)).append("</name>\n");
        sb.append("   <tag></tag>\n");
        sb.append("   <elementGuidId>").append(entityGuid).append("</elementGuidId>\n");
        sb.append("   <selectorMethod>XPATH</selectorMethod>\n");
        sb.append("   <smartLocatorEnabled>false</smartLocatorEnabled>\n");
        sb.append("   <useRalativeImagePath>false</useRalativeImagePath>\n");
        sb.append("   <selectorCollection>\n");
        sb.append("      <entry>\n");
        sb.append("         <key>XPATH</key>\n");
        sb.append("         <value>").append(sanitizeXml(xpath)).append("</value>\n");
        sb.append("      </entry>\n");
        sb.append("   </selectorCollection>\n");
        sb.append("</WebElementEntity>\n");
        return sb.toString();
    }

    /**
     * Legacy backward-compatible signature.
     */
    public String generateXmlContent(String name, String xpath, String reason, double confidence) {
        return generateMobileXmlContent(name, new HealCandidate(null, xpath, confidence, reason), null, null);
    }

    public MobileStrategy determineStrategy(UIElementNode node) {
        if (node == null) return MobileStrategy.XPATH;
        if (node.hasContentDesc()) {
            return MobileStrategy.ACCESSIBILITY;
        }
        if (node.hasResourceId() && isCleanResourceId(node.getResourceId())) {
            return MobileStrategy.ID;
        }
        if (node.hasText()) {
            return MobileStrategy.ATTRIBUTES;
        }
        return MobileStrategy.XPATH;
    }

    public String determineLocatorValue(MobileStrategy strategy, UIElementNode node, String fallbackXpath) {
        if (node == null) return fallbackXpath;
        switch (strategy) {
            case ACCESSIBILITY:
                return node.getContentDesc();
            case ID:
                return node.getResourceId();
            case ATTRIBUTES:
                return buildAttributesLocator(node);
            case XPATH:
            default:
                return fallbackXpath;
        }
    }

    public String determineSelectorMethod(MobileStrategy strategy) {
        switch (strategy) {
            case ACCESSIBILITY:
                return "ACCESSIBILITY";
            case XPATH:
                return "XPATH";
            case ID:
            case ATTRIBUTES:
            default:
                return "BASIC";
        }
    }

    public String buildAttributesLocator(UIElementNode node) {
        if (node == null) return "";
        String cls = node.getClassName();
        if (node.hasText()) {
            return "//*[@class = '" + cls + "' and (@text = '" + node.getText() + "' or . = '" + node.getText() + "')]";
        }
        if (node.hasContentDesc()) {
            return "//*[@class = '" + cls + "' and @content-desc = '" + node.getContentDesc() + "']";
        }
        if (node.hasResourceId()) {
            return "//*[@class = '" + cls + "' and @resource-id = '" + node.getResourceId() + "']";
        }
        return "//*[@class = '" + cls + "']";
    }

    public String buildUiAutomatorLocator(UIElementNode node) {
        if (node == null) return "";
        String cls = node.getClassName();
        if (node.hasContentDesc()) {
            return "new UiSelector().className(\"" + cls + "\").description(\"" + node.getContentDesc() + "\")";
        }
        if (node.hasText()) {
            return "new UiSelector().className(\"" + cls + "\").text(\"" + node.getText() + "\")";
        }
        if (node.hasResourceId()) {
            return "new UiSelector().resourceId(\"" + node.getResourceId() + "\")";
        }
        return "new UiSelector().className(\"" + cls + "\")";
    }

    public List<PropertyEntry> buildPropertyList(UIElementNode node, MobileStrategy strategy, String xpath) {
        List<PropertyEntry> list = new ArrayList<>();
        if (node == null) {
            list.add(new PropertyEntry("xpath", xpath, true));
            return list;
        }

        // 1. class
        list.add(new PropertyEntry("class", node.getClassName(), true));

        // 2. index
        list.add(new PropertyEntry("index", node.getAttribute("index", "0"), false));

        // 3. text (if present)
        if (node.hasText()) {
            list.add(new PropertyEntry("text", node.getText(), strategy == MobileStrategy.ATTRIBUTES));
        }

        // 4. package
        list.add(new PropertyEntry("package", node.getPackage(), false));

        // 5. content-desc (if present)
        if (node.hasContentDesc()) {
            list.add(new PropertyEntry("content-desc", node.getContentDesc(), strategy == MobileStrategy.ACCESSIBILITY));
        }

        // 6. checkable
        list.add(new PropertyEntry("checkable", node.getAttribute("checkable", "false"), false));

        // 7. checked
        list.add(new PropertyEntry("checked", node.getAttribute("checked", "false"), false));

        // 8. clickable
        list.add(new PropertyEntry("clickable", String.valueOf(node.isClickable()), true));

        // 9. enabled
        list.add(new PropertyEntry("enabled", String.valueOf(node.isEnabled()), false));

        // 10. focusable
        list.add(new PropertyEntry("focusable", node.getAttribute("focusable", "false"), false));

        // 11. focused
        list.add(new PropertyEntry("focused", String.valueOf(node.isFocused()), false));

        // 12. scrollable
        list.add(new PropertyEntry("scrollable", String.valueOf(node.isScrollable()), false));

        // 13. long-clickable
        list.add(new PropertyEntry("long-clickable", node.getAttribute("long-clickable", "false"), false));

        // 14. password
        list.add(new PropertyEntry("password", node.getAttribute("password", "false"), false));

        // 15. selected
        list.add(new PropertyEntry("selected", node.getAttribute("selected", "false"), false));

        // 16. bounds
        list.add(new PropertyEntry("bounds", node.getBoundsString(), false));

        // 17. displayed
        list.add(new PropertyEntry("displayed", String.valueOf(node.isDisplayed()), false));

        // 18. xpath
        String effectiveXPath = (node.getXpath() != null && !node.getXpath().isEmpty()) ? node.getXpath() : xpath;
        list.add(new PropertyEntry("xpath", effectiveXPath, strategy == MobileStrategy.XPATH));

        // 19. resource-id (if present)
        if (node.hasResourceId()) {
            list.add(new PropertyEntry("resource-id", node.getResourceId(), strategy == MobileStrategy.ID));
        }

        // Additional extra raw attributes from page source
        for (Map.Entry<String, String> entry : node.getAttributes().entrySet()) {
            String key = entry.getKey();
            if (!KNOWN_PROPS.contains(key)) {
                list.add(new PropertyEntry(key, entry.getValue(), false));
            }
        }

        return list;
    }

    private void appendCollectionEntry(StringBuilder sb, String key, String value) {
        sb.append("      <entry>\n");
        sb.append("         <key>").append(key).append("</key>\n");
        sb.append("         <value>").append(sanitizeXml(value)).append("</value>\n");
        sb.append("      </entry>\n");
    }

    private String extractExistingGuid(File targetFile) {
        if (targetFile != null && targetFile.exists()) {
            try {
                String content = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
                Pattern pattern = Pattern.compile("<elementGuidId>([a-fA-F0-9\\-]+)</elementGuidId>");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Exception ignored) {}
        }
        return UUID.randomUUID().toString();
    }

    private boolean isCleanResourceId(String resId) {
        if (resId == null || resId.trim().isEmpty()) return false;
        return !resId.matches(".*[0-9a-f]{8}-[0-9a-f]{4}-.*");
    }

    private String resolveRsRelativePath(HealContext context, HealCandidate candidate) {
        if (!context.getTargetRsPath().isEmpty()) {
            String path = context.getTargetRsPath();
            if (!path.endsWith(".rs")) {
                path += ".rs";
            }
            return path;
        }

        String screen = sanitizeFileName(context.getScreenName());
        String objName = "";
        if (candidate.getNode() != null) {
            objName = sanitizeFileName(candidate.getNode().getStrippedResourceId());
            if (objName.isEmpty()) {
                objName = sanitizeFileName(candidate.getNode().getContentDesc());
            }
            if (objName.isEmpty()) {
                objName = sanitizeFileName(candidate.getNode().getText());
            }
        }
        if (objName.isEmpty()) {
            objName = "healed_element_" + System.currentTimeMillis();
        }

        return "Object Repository/" + screen + "/" + objName + ".rs";
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "unnamed";
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private String sanitizeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
