package com.qureon.locara.core.generator;

import com.qureon.locara.core.model.UIElementNode;

import java.util.List;

/**
 * Evaluates XPath uniqueness, node tag matching, text uniqueness, and attribute predicates
 * across parsed UI element hierarchies without external DOM engine overhead.
 */
final class XPathUniquenessEvaluator {

    XPathUniquenessEvaluator() {}

    boolean isUnique(String xpath, UIElementNode target, List<UIElementNode> allNodes) {
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

    boolean isTextUnique(String targetText, List<UIElementNode> allNodes) {
        if (targetText == null || targetText.isEmpty() || allNodes == null) return false;
        int count = 0;
        for (UIElementNode n : allNodes) {
            if (targetText.equals(n.getText())) {
                count++;
                if (count > 1) return false;
            }
        }
        return count == 1;
    }

    boolean isSoleTagInstance(String tag, List<UIElementNode> allNodes) {
        if (tag == null || tag.isEmpty() || "*".equals(tag) || allNodes == null) return false;
        int count = 0;
        for (UIElementNode n : allNodes) {
            if (n.isDisplayed() && matchesTag(tag, n)) {
                count++;
                if (count > 1) return false;
            }
        }
        return count == 1;
    }

    boolean matchesTag(String expectedTag, UIElementNode node) {
        if (expectedTag == null || expectedTag.isEmpty() || "*".equals(expectedTag)) return true;
        return expectedTag.equalsIgnoreCase(node.getTag()) || expectedTag.equalsIgnoreCase(node.getClassName());
    }

    boolean matchesSimpleXPath(String xpath, UIElementNode node) {
        if (xpath == null || node == null) return false;

        int descendantIdx = xpath.indexOf("]//");
        if (xpath.startsWith("//*[") && descendantIdx != -1) {
            String parentResId = extractAttr(xpath, "@resource-id=");
            if (parentResId.isEmpty()) parentResId = extractAttr(xpath, "@id=");
            String childTag = xpath.substring(descendantIdx + 3).trim();
            if (!matchesTag(childTag, node)) return false;
            UIElementNode ancestor = node.getParent();
            while (ancestor != null) {
                if (ancestor.getResourceId().equals(parentResId) || ancestor.getAttribute("id").equals(parentResId)) {
                    return true;
                }
                ancestor = ancestor.getParent();
            }
            return false;
        }

        if (xpath.startsWith("//")) {
            int bracketIdx = xpath.indexOf('[');
            if (bracketIdx > 2 && !matchesTag(xpath.substring(2, bracketIdx).trim(), node)) {
                return false;
            }
        }

        if (xpath.contains(" and .//")) {
            if (xpath.contains("@clickable='true'") && !node.isClickable()) return false;
            if (xpath.contains("@checkable='true'") && !node.isCheckable()) return false;
            String descText = extractAttr(xpath, "@text=");
            if (!descText.isEmpty()) return hasDescendantWithAttribute(node, "text", descText);
            String descContent = extractAttr(xpath, "@content-desc=");
            if (!descContent.isEmpty()) return hasDescendantWithAttribute(node, "content-desc", descContent);
        }

        if (xpath.contains("@data-testid=")) return node.getAttribute("data-testid").equals(extractAttr(xpath, "@data-testid="));
        if (xpath.contains("@id=")) {
            String id = extractAttr(xpath, "@id=");
            return node.getAttribute("id").equals(id) || node.getResourceId().equals(id);
        }
        if (xpath.contains("@name=")) return node.getAttribute("name").equals(extractAttr(xpath, "@name="));
        if (xpath.contains("@aria-label=")) {
            String aria = extractAttr(xpath, "@aria-label=");
            return node.getAttribute("aria-label").equals(aria) || node.getContentDesc().equals(aria);
        }
        if (xpath.contains("text()=") || xpath.contains("normalize-space(text())=")) {
            String text = xpath.contains("normalize-space(text())=")
                    ? extractAttr(xpath, "normalize-space(text())=") : extractAttr(xpath, "text()=");
            return node.getText().equals(text);
        }

        if (xpath.contains("@resource-id=") && xpath.contains("@text=")) {
            return node.getResourceId().equals(extractAttr(xpath, "@resource-id="))
                    && node.getText().equals(extractAttr(xpath, "@text="));
        }
        if (xpath.contains("@resource-id=")) return node.getResourceId().equals(extractAttr(xpath, "@resource-id="));
        if (xpath.contains("@text=")) return node.getText().equals(extractAttr(xpath, "@text="));
        if (xpath.contains("@content-desc=")) return node.getContentDesc().equals(extractAttr(xpath, "@content-desc="));
        return xpath.equals(node.getXpath());
    }

    private boolean hasDescendantWithAttribute(UIElementNode container, String attrType, String expectedVal) {
        if (container == null || expectedVal == null) return false;
        for (UIElementNode child : container.getChildren()) {
            if ("text".equals(attrType) && expectedVal.equals(child.getText())) return true;
            if ("content-desc".equals(attrType) && expectedVal.equals(child.getContentDesc())) return true;
            if (hasDescendantWithAttribute(child, attrType, expectedVal)) return true;
        }
        return false;
    }

    private String extractAttr(String xpath, String prefix) {
        int start = xpath.indexOf(prefix);
        if (start == -1) return "";
        start += prefix.length();
        if (start >= xpath.length()) return "";
        if (xpath.startsWith("concat(", start)) {
            return extractConcatLiteral(xpath, start + 7);
        }
        char delimiter = xpath.charAt(start);
        if (delimiter != '\'' && delimiter != '"') return "";
        start++;
        int end = xpath.indexOf(delimiter, start);
        return end == -1 ? "" : xpath.substring(start, end);
    }

    private String extractConcatLiteral(String xpath, int innerStart) {
        int pos = innerStart;
        int closeParen = -1;
        char activeQuote = 0;
        while (pos < xpath.length()) {
            char ch = xpath.charAt(pos);
            if (activeQuote == 0) {
                if (ch == '\'' || ch == '"') activeQuote = ch;
                else if (ch == ')') { closeParen = pos; break; }
            } else if (ch == activeQuote) {
                activeQuote = 0;
            }
            pos++;
        }
        if (closeParen == -1) return "";
        String inner = xpath.substring(innerStart, closeParen);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < inner.length()) {
            char c = inner.charAt(i);
            if (c == '\'' || c == '"') {
                int endQuote = inner.indexOf(c, i + 1);
                if (endQuote == -1) break;
                sb.append(inner, i + 1, endQuote);
                i = endQuote + 1;
            } else {
                i++;
            }
        }
        return sb.toString();
    }
}
