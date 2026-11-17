package com.qureon.locara.core.generator;

import com.qureon.locara.core.generator.KatalonRsSynthesizer.MobileStrategy;
import com.qureon.locara.core.generator.KatalonRsSynthesizer.PropertyEntry;
import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.UIElementNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Renders Katalon Studio {@code MobileElementEntity} and {@code WebElementEntity} XML payloads,
 * LocatorStrategy mappings, UiAutomator2 expressions, and property tables.
 */
final class RsXmlDocumentRenderer {

    private static final Set<String> KNOWN_PROPS = new HashSet<>(Arrays.asList(
            "class", "index", "text", "package", "content-desc", "checkable", "checked",
            "clickable", "enabled", "focusable", "focused", "scrollable", "long-clickable",
            "password", "selected", "bounds", "displayed", "xpath", "resource-id", "tag"
    ));

    private final DynamicXPathGenerator xpathGenerator;
    private final RsSemanticNamingService namingService;

    RsXmlDocumentRenderer(DynamicXPathGenerator xpathGenerator, RsSemanticNamingService namingService) {
        this.xpathGenerator = xpathGenerator;
        this.namingService = namingService;
    }

    String generateMobileXmlContent(String name, HealCandidate candidate, HealContext context, String existingGuid) {
        UIElementNode node = candidate != null ? candidate.getNode() : null;
        String xpath = candidate != null ? candidate.getSuggestedXPath() : "";
        MobileStrategy strategy = determineStrategy(node, xpath);
        String entityGuid = (existingGuid != null && !existingGuid.isEmpty()) ? existingGuid : UUID.randomUUID().toString();
        String reason = candidate != null ? candidate.getHealReason() : "Self-Healing";
        double confidence = candidate != null ? candidate.getConfidenceScore() : 1.0;

        StringBuilder sb = startEntityXml("MobileElementEntity", name, reason, confidence, entityGuid, determineSelectorMethod(strategy));
        for (PropertyEntry prop : buildPropertyList(node, strategy, xpath)) {
            appendWebElementProperty(sb, prop.selected, prop.name, prop.value);
        }

        sb.append("   <locator>").append(sanitizeXml(determineLocatorValue(strategy, node, xpath))).append("</locator>\n");
        sb.append("   <locatorCollection>\n");
        appendCollectionEntry(sb, "ACCESSIBILITY", (node != null && node.hasContentDesc()) ? node.getContentDesc() : "");
        appendCollectionEntry(sb, "XPATH", xpath);
        appendCollectionEntry(sb, "ATTRIBUTES", buildAttributesLocator(node));
        appendCollectionEntry(sb, "CLASS_NAME", node != null ? node.getClassName() : "");
        appendCollectionEntry(sb, "ANDROID_UI_AUTOMATOR", buildUiAutomatorLocator(node));
        appendCollectionEntry(sb, "ID", (node != null && node.hasResourceId()) ? node.getResourceId() : "");
        for (String emptyKey : new String[]{"IMAGE", "CUSTOM", "ANDROID_VIEWTAG", "NAME", "IOS_PREDICATE_STRING", "IOS_CLASS_CHAIN"}) {
            appendCollectionEntry(sb, emptyKey, "");
        }
        sb.append("   </locatorCollection>\n");
        sb.append("   <locatorStrategy>").append(strategy.name()).append("</locatorStrategy>\n");
        sb.append("   <platform>ANDROID</platform>\n");
        sb.append("</MobileElementEntity>\n");
        return sb.toString();
    }

    String generateWebXmlContent(String name, HealCandidate candidate, HealContext context, String existingGuid) {
        String entityGuid = (existingGuid != null && !existingGuid.isEmpty()) ? existingGuid : UUID.randomUUID().toString();
        String reason = candidate != null ? candidate.getHealReason() : "Self-Healing";
        double confidence = candidate != null ? candidate.getConfidenceScore() : 1.0;
        String xpath = candidate != null ? candidate.getSuggestedXPath() : "";
        UIElementNode node = candidate != null ? candidate.getNode() : null;

        LinkedHashMap<String, String> ranked = xpathGenerator.generateRankedXPaths(
                node, null, context != null ? context.getAction() : null);
        if (ranked.isEmpty() && !xpath.isEmpty()) {
            ranked.put("xpath:attributes", xpath);
        }

        StringBuilder sb = startEntityXml("WebElementEntity", name, reason, confidence, entityGuid, "XPATH");
        for (Map.Entry<String, String> entry : ranked.entrySet()) {
            appendWebElementProperty(sb, entry.getKey().equals("xpath:attributes"), entry.getKey(), entry.getValue());
        }

        sb.append("   <selectorCollection>\n");
        appendCollectionEntry(sb, "XPATH", xpath.isEmpty() ? ranked.getOrDefault("xpath:attributes", "") : xpath);
        if (node != null && !node.getAttribute("id").isEmpty()) {
            appendCollectionEntry(sb, "CSS", "#" + node.getAttribute("id"));
        }
        sb.append("   </selectorCollection>\n");
        sb.append("</WebElementEntity>\n");
        return sb.toString();
    }

    MobileStrategy determineStrategy(UIElementNode node, String xpath) {
        if (node == null || isComplexOrRelationalXPath(node, xpath)) return MobileStrategy.XPATH;
        if (node.hasContentDesc()) return MobileStrategy.ACCESSIBILITY;
        if (node.hasResourceId() && namingService.isCleanResourceId(node.getResourceId())) return MobileStrategy.ID;
        if (node.hasText()) return MobileStrategy.ATTRIBUTES;
        return MobileStrategy.XPATH;
    }

    static boolean isComplexOrRelationalXPath(UIElementNode node, String xpath) {
        if (xpath == null || xpath.isEmpty()) return false;
        if (xpath.contains(".//") || xpath.contains("following-sibling::") || xpath.contains("preceding-sibling::")
                || xpath.contains("]//") || xpath.startsWith("(") || xpath.startsWith("/hierarchy") || xpath.startsWith("//hierarchy")) {
            return true;
        }
        return node != null && !node.getTag().isEmpty() && xpath.startsWith("//") && !xpath.startsWith("//*")
                && !xpath.startsWith("//" + node.getTag());
    }

    String determineLocatorValue(MobileStrategy strategy, UIElementNode node, String fallbackXpath) {
        if (node == null) return fallbackXpath;
        switch (strategy) {
            case ACCESSIBILITY: return node.getContentDesc();
            case ID: return node.getResourceId();
            case ATTRIBUTES: return buildAttributesLocator(node);
            case XPATH:
            default: return fallbackXpath;
        }
    }

    String determineSelectorMethod(MobileStrategy strategy) {
        if (strategy == MobileStrategy.ACCESSIBILITY) return "ACCESSIBILITY";
        if (strategy == MobileStrategy.XPATH) return "XPATH";
        return "BASIC";
    }

    String buildAttributesLocator(UIElementNode node) {
        if (node == null) return "";
        String clsLit = DynamicXPathGenerator.toXPathLiteral(node.getClassName());
        if (node.hasText()) {
            String textLit = DynamicXPathGenerator.toXPathLiteral(node.getText());
            return "//*[@class = " + clsLit + " and (@text = " + textLit + " or . = " + textLit + ")]";
        }
        if (node.hasContentDesc()) {
            return "//*[@class = " + clsLit + " and @content-desc = " + DynamicXPathGenerator.toXPathLiteral(node.getContentDesc()) + "]";
        }
        if (node.hasResourceId()) {
            return "//*[@class = " + clsLit + " and @resource-id = " + DynamicXPathGenerator.toXPathLiteral(node.getResourceId()) + "]";
        }
        return "//*[@class = " + clsLit + "]";
    }

    String buildUiAutomatorLocator(UIElementNode node) {
        if (node == null) return "";
        String cls = escapeJavaString(node.getClassName());
        if (node.hasContentDesc()) return "new UiSelector().className(\"" + cls + "\").description(\"" + escapeJavaString(node.getContentDesc()) + "\")";
        if (node.hasText()) return "new UiSelector().className(\"" + cls + "\").text(\"" + escapeJavaString(node.getText()) + "\")";
        if (node.hasResourceId()) return "new UiSelector().resourceId(\"" + escapeJavaString(node.getResourceId()) + "\")";
        return "new UiSelector().className(\"" + cls + "\")";
    }

    List<PropertyEntry> buildPropertyList(UIElementNode node, MobileStrategy strategy, String xpath) {
        List<PropertyEntry> list = new ArrayList<>();
        if (node == null) {
            list.add(new PropertyEntry("xpath", xpath, true));
            return list;
        }
        list.add(new PropertyEntry("class", node.getClassName(), true));
        list.add(new PropertyEntry("index", node.getAttribute("index", "0"), false));
        if (node.hasText()) list.add(new PropertyEntry("text", node.getText(), strategy == MobileStrategy.ATTRIBUTES));
        list.add(new PropertyEntry("package", node.getPackage(), false));
        if (node.hasContentDesc()) list.add(new PropertyEntry("content-desc", node.getContentDesc(), strategy == MobileStrategy.ACCESSIBILITY));
        list.add(new PropertyEntry("checkable", node.getAttribute("checkable", "false"), false));
        list.add(new PropertyEntry("checked", node.getAttribute("checked", "false"), false));
        list.add(new PropertyEntry("clickable", String.valueOf(node.isClickable()), true));
        list.add(new PropertyEntry("enabled", String.valueOf(node.isEnabled()), false));
        list.add(new PropertyEntry("focusable", node.getAttribute("focusable", "false"), false));
        list.add(new PropertyEntry("focused", String.valueOf(node.isFocused()), false));
        list.add(new PropertyEntry("scrollable", String.valueOf(node.isScrollable()), false));
        list.add(new PropertyEntry("long-clickable", node.getAttribute("long-clickable", "false"), false));
        list.add(new PropertyEntry("password", node.getAttribute("password", "false"), false));
        list.add(new PropertyEntry("selected", node.getAttribute("selected", "false"), false));
        list.add(new PropertyEntry("bounds", node.getBoundsString(), false));
        list.add(new PropertyEntry("displayed", String.valueOf(node.isDisplayed()), false));
        String effectiveXPath = (xpath != null && !xpath.isEmpty()) ? xpath : node.getXpath();
        list.add(new PropertyEntry("xpath", effectiveXPath, strategy == MobileStrategy.XPATH));
        if (node.hasResourceId()) list.add(new PropertyEntry("resource-id", node.getResourceId(), strategy == MobileStrategy.ID));

        for (Map.Entry<String, String> entry : node.getAttributes().entrySet()) {
            if (!KNOWN_PROPS.contains(entry.getKey())) list.add(new PropertyEntry(entry.getKey(), entry.getValue(), false));
        }
        return list;
    }

    private StringBuilder startEntityXml(String rootTag, String name, String reason, double confidence, String guid, String selectorMethod) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<").append(rootTag).append(">\n");
        sb.append("   <description>Auto-generated by Locara by Qureon Engine (")
                .append(sanitizeXml(reason)).append(" - Confidence: ")
                .append(String.format(Locale.US, "%.1f%%", confidence * 100.0)).append(")</description>\n");
        sb.append("   <name>").append(sanitizeXml(name)).append("</name>\n");
        sb.append("   <tag></tag>\n");
        sb.append("   <elementGuidId>").append(guid).append("</elementGuidId>\n");
        sb.append("   <selectorMethod>").append(selectorMethod).append("</selectorMethod>\n");
        sb.append("   <smartLocatorEnabled>false</smartLocatorEnabled>\n");
        sb.append("   <useRalativeImagePath>false</useRalativeImagePath>\n");
        return sb;
    }

    private void appendWebElementProperty(StringBuilder sb, boolean selected, String name, String value) {
        sb.append("   <webElementProperties>\n");
        sb.append("      <isSelected>").append(selected).append("</isSelected>\n");
        sb.append("      <matchCondition>equals</matchCondition>\n");
        sb.append("      <name>").append(sanitizeXml(name)).append("</name>\n");
        sb.append("      <type>Main</type>\n");
        sb.append("      <value>").append(sanitizeXml(value)).append("</value>\n");
        sb.append("      <webElementGuid>").append(UUID.randomUUID().toString()).append("</webElementGuid>\n");
        sb.append("   </webElementProperties>\n");
    }

    private void appendCollectionEntry(StringBuilder sb, String key, String value) {
        sb.append("      <entry>\n         <key>").append(key).append("</key>\n");
        sb.append("         <value>").append(sanitizeXml(value)).append("</value>\n      </entry>\n");
    }

    private String escapeJavaString(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String sanitizeXml(String input) {
        return input == null ? "" : input.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
