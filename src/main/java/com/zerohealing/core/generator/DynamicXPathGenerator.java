package com.zerohealing.core.generator;

import com.zerohealing.core.model.UIElementNode;

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
    public String generateOptimalXPath(UIElementNode targetNode, List<UIElementNode> allNodes, String action) {
        if (targetNode == null) return "";

        String tag = targetNode.getTag().isEmpty() ? "*" : targetNode.getTag();
        String resourceId = targetNode.getResourceId();
        String text = targetNode.getText();
        String contentDesc = targetNode.getContentDesc();

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
        // Quick evaluator for standard formats
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
