package com.qureon.locara.core.generator;

import com.qureon.locara.core.model.UIElementNode;

import java.util.List;

/**
 * Synthesizes robust, clean, and unambiguous XPath locators for mobile elements.
 */
public class DynamicXPathGenerator {

    public DynamicXPathGenerator() {}

    /**
     * Generates the most deterministic and minimal XPath for the given candidate node.
     */
    public String generateOptimalXPath(UIElementNode targetNode, List<UIElementNode> allNodes) {
        return generateOptimalXPath(targetNode, allNodes, null);
    }

    /**
     * Generates the most deterministic and minimal XPath for the given candidate node,
     * taking into account the intended UI action (e.g., TAP vs GET_TEXT).
     */
    private static final java.util.Set<String> HTML_TAGS = new java.util.HashSet<>(java.util.Arrays.asList(
            "html", "body", "div", "span", "p", "a", "button", "input", "select", "option",
            "textarea", "form", "table", "tr", "td", "th", "tbody", "thead", "ul", "ol", "li",
            "h1", "h2", "h3", "h4", "h5", "h6", "label", "img", "nav", "header", "footer", "section", "article"
    ));

    public boolean isHtmlNode(UIElementNode node) {
        if (node == null) return false;
        String tag = node.getTag().toLowerCase();
        return HTML_TAGS.contains(tag) || node.getAttributes().containsKey("data-testid") || node.getAttributes().containsKey("role");
    }

    /**
     * Generates the most deterministic and minimal XPath for the given candidate node,
     * taking into account the intended UI action (e.g., TAP vs GET_TEXT).
     */
    public String generateOptimalXPath(UIElementNode targetNode, List<UIElementNode> allNodes, String action) {
        if (targetNode == null) return "";

        String tag = targetNode.getTag().isEmpty() ? "*" : targetNode.getTag();
        String resourceId = targetNode.getResourceId();
        String text = targetNode.getText();
        String contentDesc = targetNode.getContentDesc();

        // --- HYBRID WEBVIEW / HTML DOM STRATEGIES ---
        if (isHtmlNode(targetNode)) {
            // HTML Strategy 1: data-testid (Universal Modern Standard: React, Angular, Vue, Ionic)
            String testId = targetNode.getAttribute("data-testid");
            if (!testId.isEmpty()) {
                String xpath = String.format("//%s[@data-testid='%s']", tag, testId);
                if (isUnique(xpath, targetNode, allNodes)) return xpath;
                String wild = String.format("//*[@data-testid='%s']", testId);
                if (isUnique(wild, targetNode, allNodes)) return wild;
            }

            // HTML Strategy 2: id attribute
            String idAttr = targetNode.getAttribute("id");
            if (!idAttr.isEmpty()) {
                String xpath = String.format("//%s[@id='%s']", tag, idAttr);
                if (isUnique(xpath, targetNode, allNodes)) return xpath;
                String wild = String.format("//*[@id='%s']", idAttr);
                if (isUnique(wild, targetNode, allNodes)) return wild;
            }

            // HTML Strategy 3: name attribute (Form inputs)
            String nameAttr = targetNode.getAttribute("name");
            if (!nameAttr.isEmpty()) {
                String xpath = String.format("//%s[@name='%s']", tag, nameAttr);
                if (isUnique(xpath, targetNode, allNodes)) return xpath;
            }

            // HTML Strategy 4: aria-label
            String ariaLabel = targetNode.getAttribute("aria-label");
            if (!ariaLabel.isEmpty()) {
                String xpath = String.format("//%s[@aria-label=%s]", tag, toXPathLiteral(ariaLabel));
                if (isUnique(xpath, targetNode, allNodes)) return xpath;
            }

            // HTML Strategy 5: Visible Text
            if (!text.isEmpty()) {
                String xpath = String.format("//%s[text()=%s]", tag, toXPathLiteral(text));
                if (isUnique(xpath, targetNode, allNodes)) return xpath;
                String normXpath = String.format("//%s[normalize-space(text())=%s]", tag, toXPathLiteral(text));
                if (isUnique(normXpath, targetNode, allNodes)) return normXpath;
            }

            // Fallback: structural XPath from HTML DOM
            if (!targetNode.getXpath().isEmpty()) {
                return targetNode.getXpath();
            }
        }

        // --- NATIVE MOBILE XML STRATEGIES (Android UIAutomator2 / iOS XCUITest) ---
        // Strategy 1: Resource-ID + Text combination (Ultra Robust)
        if (!resourceId.isEmpty() && !text.isEmpty()) {
            String xpath = String.format("//%s[@resource-id='%s' and @text=%s]", tag, resourceId, toXPathLiteral(text));
            if (isUnique(xpath, targetNode, allNodes)) {
                return xpath;
            }
        }

        // Strategy 2: Resource-ID alone (if unique)
        if (!resourceId.isEmpty()) {
            String xpath = String.format("//%s[@resource-id='%s']", tag, resourceId);
            if (isUnique(xpath, targetNode, allNodes)) {
                return xpath;
            }
            // Or wildcard tag with unique resource-id
            String anyTagXPath = String.format("//*[@resource-id='%s']", resourceId);
            if (isUnique(anyTagXPath, targetNode, allNodes)) {
                return anyTagXPath;
            }
        }

        // Strategy 3: React Native Clickable Parent Container Ascension (for TAP/CLICK actions)
        // Solves CButton.tsx / TouchableOpacity pattern where TextView is clickable="false"
        // but ancestor ViewGroup is clickable="true" with onPress handler.
        if (("TAP".equalsIgnoreCase(action) || "CLICK".equalsIgnoreCase(action)) &&
                !targetNode.isClickable() && !text.isEmpty()) {
            UIElementNode clickableAncestor = findClickableAncestor(targetNode);
            if (clickableAncestor != null) {
                String containerTag = clickableAncestor.getTag().isEmpty() ? "android.view.ViewGroup" : clickableAncestor.getTag();
                return String.format("//%s[@clickable='true' and .//%s[@text=%s]]",
                        containerTag, tag, toXPathLiteral(text));
            }
        }

        // Strategy 4: Text alone (if unique)
        if (!text.isEmpty()) {
            String xpath = String.format("//%s[@text=%s]", tag, toXPathLiteral(text));
            if (isUnique(xpath, targetNode, allNodes)) {
                return xpath;
            }
        }

        // Strategy 5: Content-Desc alone (if unique)
        if (!contentDesc.isEmpty()) {
            String xpath = String.format("//%s[@content-desc=%s]", tag, toXPathLiteral(contentDesc));
            if (isUnique(xpath, targetNode, allNodes)) {
                return xpath;
            }
        }

        // Strategy 6: Parent Anchor + Relative Child
        if (targetNode.getParent() != null) {
            UIElementNode parent = targetNode.getParent();
            if (!parent.getResourceId().isEmpty()) {
                String parentXPath = String.format("//*[@resource-id='%s']//%s", parent.getResourceId(), tag);
                if (isUnique(parentXPath, targetNode, allNodes)) {
                    return parentXPath;
                }
            }
        }

        // Fallback: Exact structural XPath from parser
        return targetNode.getXpath();
    }

    private UIElementNode findClickableAncestor(UIElementNode node) {
        if (node == null) return null;
        UIElementNode current = node.getParent();
        int levels = 0;
        while (current != null && levels < 4) {
            if (current.isClickable() &&
                    (current.getClassName().contains("ViewGroup") || current.getTag().contains("ViewGroup") ||
                     current.getClassName().contains("Layout") || current.getTag().contains("Layout") ||
                     current.getClassName().contains("Button") || current.getTag().contains("Button"))) {
                return current;
            }
            current = current.getParent();
            levels++;
        }
        return null;
    }

    private boolean isUnique(String xpath, UIElementNode target, List<UIElementNode> allNodes) {
        if (allNodes == null || allNodes.isEmpty()) return true;
        int matchCount = 0;
        for (UIElementNode node : allNodes) {
            if (matchesSimpleXPath(xpath, node)) {
                matchCount++;
                if (matchCount > 1) return false;
            }
        }
        return matchCount == 1;
    }

    private boolean matchesSimpleXPath(String xpath, UIElementNode node) {
        // HTML DOM attribute evaluators
        if (xpath.contains("@data-testid=")) {
            String testId = extractAttr(xpath, "@data-testid=");
            return node.getAttribute("data-testid").equals(testId);
        } else if (xpath.contains("@id=")) {
            String id = extractAttr(xpath, "@id=");
            return node.getAttribute("id").equals(id) || node.getResourceId().equals(id);
        } else if (xpath.contains("@name=")) {
            String name = extractAttr(xpath, "@name=");
            return node.getAttribute("name").equals(name);
        } else if (xpath.contains("@aria-label=")) {
            String aria = extractAttr(xpath, "@aria-label=");
            return node.getAttribute("aria-label").equals(aria) || node.getContentDesc().equals(aria);
        } else if (xpath.contains("text()=") || xpath.contains("normalize-space(text())=")) {
            String text = xpath.contains("normalize-space(text())=") ?
                    extractAttr(xpath, "normalize-space(text())=") : extractAttr(xpath, "text()=");
            return node.getText().equals(text);
        }

        // Native Mobile attribute evaluators
        if (xpath.contains("@resource-id=") && xpath.contains("@text=")) {
            String resId = extractAttr(xpath, "@resource-id=");
            String text = extractAttr(xpath, "@text=");
            return node.getResourceId().equals(resId) && node.getText().equals(text);
        } else if (xpath.contains("@resource-id=")) {
            String resId = extractAttr(xpath, "@resource-id=");
            return node.getResourceId().equals(resId);
        } else if (xpath.contains("@text=")) {
            String text = extractAttr(xpath, "@text=");
            return node.getText().equals(text);
        } else if (xpath.contains("@content-desc=")) {
            String desc = extractAttr(xpath, "@content-desc=");
            return node.getContentDesc().equals(desc);
        }
        return xpath.equals(node.getXpath());
    }

    private String extractAttr(String xpath, String prefix) {
        int start = xpath.indexOf(prefix);
        if (start == -1) return "";
        start += prefix.length();
        if (start >= xpath.length()) return "";
        char delimiter = xpath.charAt(start);
        if (delimiter != '\'' && delimiter != '"') return "";
        start++;
        int end = xpath.indexOf(delimiter, start);
        if (end == -1) return "";
        return xpath.substring(start, end);
    }

    public static String toXPathLiteral(String text) {
        if (text == null) return "''";
        if (!text.contains("'")) {
            return "'" + text + "'";
        }
        if (!text.contains("\"")) {
            return "\"" + text + "\"";
        }
        StringBuilder sb = new StringBuilder("concat(");
        String[] parts = text.split("'", -1);
        for (int i = 0; i < parts.length; i++) {
            sb.append("'").append(parts[i]).append("'");
            if (i < parts.length - 1) {
                sb.append(", \"'\", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
