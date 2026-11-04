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
    /**
     * Generates a ranked map of alternative XPath locators for the given node,
     * mirroring Katalon Studio 11.6.0+ Object's XPaths table (xpath:attributes, xpath:idRelative, xpath:position, xpath:custom).
     */
    public java.util.LinkedHashMap<String, String> generateRankedXPaths(UIElementNode targetNode, List<UIElementNode> allNodes, String action) {
        java.util.LinkedHashMap<String, String> ranked = new java.util.LinkedHashMap<>();
        if (targetNode == null) return ranked;

        String primary = generateOptimalXPath(targetNode, allNodes, action);
        if (!primary.isEmpty()) {
            ranked.put("xpath:attributes", primary);
        }

        String tag = targetNode.getTag().isEmpty() ? "*" : targetNode.getTag();
        String text = targetNode.getText();
        String resourceId = targetNode.getResourceId();

        // 1. Structural / Relative Parent Strategy
        if (targetNode.getParent() != null) {
            UIElementNode parent = targetNode.getParent();
            String parentId = parent.getResourceId().isEmpty() ? parent.getAttribute("id") : parent.getResourceId();
            if (!parentId.isEmpty()) {
                ranked.put("xpath:idRelative", String.format("//*[@id=%s]//%s", toXPathLiteral(parentId), tag));
            } else if (!parent.getTag().isEmpty()) {
                ranked.put("xpath:idRelative", String.format("//%s/%s", parent.getTag(), tag));
            }
        }

        // 2. Visible text / Custom Strategy
        if (!text.isEmpty()) {
            ranked.put("xpath:custom", String.format("//%s[text()=%s or .=%s]", tag, toXPathLiteral(text), toXPathLiteral(text)));
        }

        // 3. Fallback Hierarchy Position Strategy
        if (!targetNode.getXpath().isEmpty()) {
            ranked.put("xpath:position", targetNode.getXpath());
        }

        return ranked;
    }

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
                String xpath = String.format("//%s[@data-testid=%s]", tag, toXPathLiteral(testId));
                if (isUnique(xpath, targetNode, allNodes)) return xpath;
                String wild = String.format("//*[@data-testid=%s]", toXPathLiteral(testId));
                if (isUnique(wild, targetNode, allNodes)) return wild;
            }

            // HTML Strategy 2: id attribute
            String idAttr = targetNode.getAttribute("id");
            if (!idAttr.isEmpty()) {
                String xpath = String.format("//%s[@id=%s]", tag, toXPathLiteral(idAttr));
                if (isUnique(xpath, targetNode, allNodes)) return xpath;
                String wild = String.format("//*[@id=%s]", toXPathLiteral(idAttr));
                if (isUnique(wild, targetNode, allNodes)) return wild;
            }

            // HTML Strategy 3: name attribute (Form inputs)
            String nameAttr = targetNode.getAttribute("name");
            if (!nameAttr.isEmpty()) {
                String xpath = String.format("//%s[@name=%s]", tag, toXPathLiteral(nameAttr));
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
            String xpath = String.format("//%s[@resource-id=%s and @text=%s]", tag, toXPathLiteral(resourceId), toXPathLiteral(text));
            if (isUnique(xpath, targetNode, allNodes)) {
                return xpath;
            }
        }

        // Strategy 2: Resource-ID alone (if unique)
        if (!resourceId.isEmpty()) {
            String xpath = String.format("//%s[@resource-id=%s]", tag, toXPathLiteral(resourceId));
            if (isUnique(xpath, targetNode, allNodes)) {
                return xpath;
            }
            // Or wildcard tag with unique resource-id
            String anyTagXPath = String.format("//*[@resource-id=%s]", toXPathLiteral(resourceId));
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

        // Strategy 3B: Clickable/Checkable Container wrapping a unique child TextView (when targetNode itself is the ViewGroup)
        if ((targetNode.isClickable() || targetNode.isCheckable()) && text.isEmpty() && resourceId.isEmpty() && contentDesc.isEmpty()) {
            UIElementNode childTextNode = findFirstTextDescendant(targetNode, 2);
            if (childTextNode != null && isTextUnique(childTextNode.getText(), allNodes)) {
                String childTag = childTextNode.getTag().isEmpty() ? "android.widget.TextView" : childTextNode.getTag();
                String attrPred = targetNode.isClickable() ? "@clickable='true'" : "@checkable='true'";
                return String.format("//%s[%s and .//%s[@text=%s]]",
                        tag, attrPred, childTag, toXPathLiteral(childTextNode.getText()));
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
                String parentXPath = String.format("//*[@resource-id=%s]//%s", toXPathLiteral(parent.getResourceId()), tag);
                if (isUnique(parentXPath, targetNode, allNodes)) {
                    return parentXPath;
                }
            }
        }

        // Strategy 6B: Sibling / Row Container Label Anchor XPath (for anonymous EditText, CheckBox, or ViewGroup checkbox)
        String relationalXPath = synthesizeSiblingAnchorXPath(targetNode, allNodes);
        if (!relationalXPath.isEmpty()) {
            return relationalXPath;
        }

        // Strategy 6C: Unique Widget Tag on Screen (e.g. sole EditText or sole CheckBox on the active screen)
        if ((targetNode.isInputWidget() || targetNode.isCheckboxOrToggleWidget()) && isSoleTagInstance(tag, allNodes)) {
            return "//" + tag;
        }

        // Fallback: Exact structural XPath from parser
        return targetNode.getXpath();
    }

    private UIElementNode findFirstTextDescendant(UIElementNode container, int maxDepth) {
        if (container == null || maxDepth <= 0) return null;
        for (UIElementNode child : container.getChildren()) {
            if (child.isDisplayed() && child.hasText()) {
                return child;
            }
            UIElementNode nested = findFirstTextDescendant(child, maxDepth - 1);
            if (nested != null) return nested;
        }
        return null;
    }

    private boolean isTextUnique(String targetText, List<UIElementNode> allNodes) {
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

    private boolean isSoleTagInstance(String tag, List<UIElementNode> allNodes) {
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

    private String synthesizeSiblingAnchorXPath(UIElementNode targetNode, List<UIElementNode> allNodes) {
        if (targetNode == null || targetNode.getParent() == null) return "";
        String targetTag = targetNode.getTag().isEmpty() ? "*" : targetNode.getTag();

        // Case 1: Direct sibling TextView inside the same parent (e.g., [CheckBox/ViewGroup] + [TextView] or [TextView] + [EditText])
        UIElementNode parent = targetNode.getParent();
        List<UIElementNode> siblings = parent.getChildren();
        int targetIdx = siblings.indexOf(targetNode);
        int sameTagSiblings = 0;
        for (UIElementNode sib : siblings) {
            if (matchesTag(targetTag, sib)) sameTagSiblings++;
        }

        if (sameTagSiblings == 1) {
            for (int i = 0; i < siblings.size(); i++) {
                if (i == targetIdx) continue;
                UIElementNode sib = siblings.get(i);
                if (sib.isDisplayed() && sib.hasText() && isTextUnique(sib.getText(), allNodes)) {
                    String sibTag = sib.getTag().isEmpty() ? "android.widget.TextView" : sib.getTag();
                    String axis = (i > targetIdx) ? "following-sibling::" : "preceding-sibling::";
                    if (targetNode.isInputWidget() || targetNode.isCheckboxOrToggleWidget()) {
                        return String.format("//%s[%s%s[@text=%s]]",
                                targetTag, axis, sibTag, toXPathLiteral(sib.getText()));
                    } else if (targetNode.isClickable() || targetNode.isCheckable()) {
                        String attr = targetNode.isClickable() ? "@clickable='true'" : "@checkable='true'";
                        return String.format("//%s[%s and %s%s[@text=%s]]",
                                targetTag, attr, axis, sibTag, toXPathLiteral(sib.getText()));
                    }
                }
            }
        }

        // Case 2: React Native nested Input Box (Row ViewGroup -> [TextView label] + [Input Box ViewGroup -> EditText])
        if (targetNode.isInputWidget() && parent.getParent() != null) {
            UIElementNode grandParent = parent.getParent();
            int inputsInGrandParent = countDescendantTag(grandParent, targetTag);
            if (inputsInGrandParent == 1) {
                for (UIElementNode aunt : grandParent.getChildren()) {
                    if (aunt != parent && aunt.isDisplayed() && aunt.hasText() && isTextUnique(aunt.getText(), allNodes)) {
                        String gpTag = grandParent.getTag().isEmpty() ? "android.view.ViewGroup" : grandParent.getTag();
                        String auntTag = aunt.getTag().isEmpty() ? "android.widget.TextView" : aunt.getTag();
                        return String.format("//%s[./%s[@text=%s]]//%s",
                                gpTag, auntTag, toXPathLiteral(aunt.getText()), targetTag);
                    }
                }
            }
        }

        return "";
    }

    private int countDescendantTag(UIElementNode container, String tag) {
        if (container == null) return 0;
        int count = 0;
        for (UIElementNode child : container.getChildren()) {
            if (matchesTag(tag, child)) count++;
            count += countDescendantTag(child, tag);
        }
        return count;
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
        if (xpath == null || node == null) return false;

        // Strategy 6: Parent relative descendant XPath //*[@resource-id='...']//tag
        int descendantIdx = xpath.indexOf("]//");
        if (xpath.startsWith("//*[") && descendantIdx != -1) {
            String parentResId = extractAttr(xpath, "@resource-id=");
            if (parentResId.isEmpty()) {
                parentResId = extractAttr(xpath, "@id=");
            }
            String childTag = xpath.substring(descendantIdx + 3).trim();
            if (!matchesTag(childTag, node)) {
                return false;
            }
            UIElementNode ancestor = node.getParent();
            while (ancestor != null) {
                if (ancestor.getResourceId().equals(parentResId) || ancestor.getAttribute("id").equals(parentResId)) {
                    return true;
                }
                ancestor = ancestor.getParent();
            }
            return false;
        }

        // Verify tag constraint for single-element XPaths starting with //<tag>[
        if (xpath.startsWith("//")) {
            int bracketIdx = xpath.indexOf('[');
            if (bracketIdx > 2) {
                String expectedTag = xpath.substring(2, bracketIdx).trim();
                if (!matchesTag(expectedTag, node)) {
                    return false;
                }
            }
        }

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

    private boolean matchesTag(String expectedTag, UIElementNode node) {
        if (expectedTag == null || expectedTag.isEmpty() || "*".equals(expectedTag)) {
            return true;
        }
        return expectedTag.equalsIgnoreCase(node.getTag()) || expectedTag.equalsIgnoreCase(node.getClassName());
    }

    private String extractAttr(String xpath, String prefix) {
        int start = xpath.indexOf(prefix);
        if (start == -1) return "";
        start += prefix.length();
        if (start >= xpath.length()) return "";
        if (xpath.startsWith("concat(", start)) {
            int closeParen = xpath.indexOf(')', start + 7);
            if (closeParen == -1) return "";
            String inner = xpath.substring(start + 7, closeParen);
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
