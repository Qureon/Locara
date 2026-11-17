package com.qureon.locara.core.generator;

import com.qureon.locara.core.model.UIElementNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Synthesizes robust, minimal, unambiguous XPath locators for native Mobile and Hybrid HTML WebView elements.
 */
public class DynamicXPathGenerator {

    private static final Set<String> HTML_TAGS = new HashSet<>(Arrays.asList(
            "html", "body", "div", "span", "p", "a", "button", "input", "select", "option",
            "textarea", "form", "table", "tr", "td", "th", "tbody", "thead", "ul", "ol", "li",
            "h1", "h2", "h3", "h4", "h5", "h6", "label", "img", "nav", "header", "footer", "section", "article"
    ));

    private final XPathUniquenessEvaluator evaluator = new XPathUniquenessEvaluator();
    private final RelationalXPathBuilder relationalBuilder = new RelationalXPathBuilder(evaluator);

    public DynamicXPathGenerator() {}

    public String generateOptimalXPath(UIElementNode targetNode, List<UIElementNode> allNodes) {
        return generateOptimalXPath(targetNode, allNodes, null);
    }

    public boolean isHtmlNode(UIElementNode node) {
        if (node == null) return false;
        String tag = node.getTag().toLowerCase();
        return HTML_TAGS.contains(tag) || node.getAttributes().containsKey("data-testid") || node.getAttributes().containsKey("role");
    }

    public LinkedHashMap<String, String> generateRankedXPaths(UIElementNode targetNode, List<UIElementNode> allNodes, String action) {
        LinkedHashMap<String, String> ranked = new LinkedHashMap<>();
        if (targetNode == null) return ranked;

        String primary = generateOptimalXPath(targetNode, allNodes, action);
        if (!primary.isEmpty()) ranked.put("xpath:attributes", primary);

        String tag = targetNode.getTag().isEmpty() ? "*" : targetNode.getTag();
        String text = targetNode.getText();
        if (targetNode.getParent() != null) {
            UIElementNode parent = targetNode.getParent();
            if (!isHtmlNode(targetNode) && !parent.getResourceId().isEmpty()) {
                ranked.put("xpath:idRelative", String.format("//*[@resource-id=%s]//%s", toXPathLiteral(parent.getResourceId()), tag));
            } else {
                String parentId = !parent.getAttribute("id").isEmpty() ? parent.getAttribute("id") : parent.getResourceId();
                if (!parentId.isEmpty()) {
                    ranked.put("xpath:idRelative", String.format("//*[@id=%s]//%s", toXPathLiteral(parentId), tag));
                } else if (!parent.getTag().isEmpty()) {
                    ranked.put("xpath:idRelative", String.format("//%s/%s", parent.getTag(), tag));
                }
            }
        }
        if (!text.isEmpty()) {
            ranked.put("xpath:custom", String.format("//%s[text()=%s or .=%s]", tag, toXPathLiteral(text), toXPathLiteral(text)));
        }
        if (!targetNode.getXpath().isEmpty()) {
            ranked.put("xpath:position", targetNode.getXpath());
        }
        return ranked;
    }

    public String generateOptimalXPath(UIElementNode targetNode, List<UIElementNode> allNodes, String action) {
        if (targetNode == null) return "";
        if (isHtmlNode(targetNode)) {
            String htmlXPath = generateHtmlXPath(targetNode, allNodes);
            if (!htmlXPath.isEmpty()) return htmlXPath;
        }
        return generateMobileXPath(targetNode, allNodes, action);
    }

    private String generateHtmlXPath(UIElementNode node, List<UIElementNode> allNodes) {
        String tag = node.getTag().isEmpty() ? "*" : node.getTag();
        for (String attr : new String[]{"data-testid", "id", "name", "aria-label"}) {
            String val = node.getAttribute(attr);
            if (!val.isEmpty()) {
                String xpath = String.format("//%s[@%s=%s]", tag, attr, toXPathLiteral(val));
                if (evaluator.isUnique(xpath, node, allNodes)) return xpath;
                if ("data-testid".equals(attr) || "id".equals(attr)) {
                    String wild = String.format("//*[@%s=%s]", attr, toXPathLiteral(val));
                    if (evaluator.isUnique(wild, node, allNodes)) return wild;
                }
            }
        }
        String text = node.getText();
        if (!text.isEmpty()) {
            String xpath = String.format("//%s[text()=%s]", tag, toXPathLiteral(text));
            if (evaluator.isUnique(xpath, node, allNodes)) return xpath;
            String normXpath = String.format("//%s[normalize-space(text())=%s]", tag, toXPathLiteral(text));
            if (evaluator.isUnique(normXpath, node, allNodes)) return normXpath;
        }
        return node.getXpath();
    }

    private String generateMobileXPath(UIElementNode targetNode, List<UIElementNode> allNodes, String action) {
        String tag = targetNode.getTag().isEmpty() ? "*" : targetNode.getTag();
        String resourceId = targetNode.getResourceId();
        String text = targetNode.getText();
        String contentDesc = targetNode.getContentDesc();

        if (!resourceId.isEmpty() && !text.isEmpty()) {
            String xpath = String.format("//%s[@resource-id=%s and @text=%s]", tag, toXPathLiteral(resourceId), toXPathLiteral(text));
            if (evaluator.isUnique(xpath, targetNode, allNodes)) return xpath;
        }
        if (!resourceId.isEmpty()) {
            String xpath = String.format("//%s[@resource-id=%s]", tag, toXPathLiteral(resourceId));
            if (evaluator.isUnique(xpath, targetNode, allNodes)) return xpath;
            String anyTagXPath = String.format("//*[@resource-id=%s]", toXPathLiteral(resourceId));
            if (evaluator.isUnique(anyTagXPath, targetNode, allNodes)) return anyTagXPath;
        }
        if (("TAP".equalsIgnoreCase(action) || "CLICK".equalsIgnoreCase(action)) && !targetNode.isClickable() && !text.isEmpty()) {
            UIElementNode clickableAncestor = relationalBuilder.findClickableAncestor(targetNode);
            if (clickableAncestor != null) {
                String containerTag = clickableAncestor.getTag().isEmpty() ? "android.view.ViewGroup" : clickableAncestor.getTag();
                return String.format("//%s[@clickable='true' and .//%s[@text=%s]]", containerTag, tag, toXPathLiteral(text));
            }
        }
        if ((targetNode.isClickable() || targetNode.isCheckable()) && text.isEmpty() && resourceId.isEmpty() && contentDesc.isEmpty()) {
            UIElementNode childTextNode = relationalBuilder.findFirstTextDescendant(targetNode, 2);
            if (childTextNode != null && evaluator.isTextUnique(childTextNode.getText(), allNodes)) {
                String childTag = childTextNode.getTag().isEmpty() ? "android.widget.TextView" : childTextNode.getTag();
                String attrPred = targetNode.isClickable() ? "@clickable='true'" : "@checkable='true'";
                return String.format("//%s[%s and .//%s[@text=%s]]", tag, attrPred, childTag, toXPathLiteral(childTextNode.getText()));
            }
        }
        if (!text.isEmpty()) {
            String xpath = String.format("//%s[@text=%s]", tag, toXPathLiteral(text));
            if (evaluator.isUnique(xpath, targetNode, allNodes)) return xpath;
        }
        if (!contentDesc.isEmpty()) {
            String xpath = String.format("//%s[@content-desc=%s]", tag, toXPathLiteral(contentDesc));
            if (evaluator.isUnique(xpath, targetNode, allNodes)) return xpath;
        }
        if (targetNode.getParent() != null && !targetNode.getParent().getResourceId().isEmpty()) {
            String parentXPath = String.format("//*[@resource-id=%s]//%s", toXPathLiteral(targetNode.getParent().getResourceId()), tag);
            if (evaluator.isUnique(parentXPath, targetNode, allNodes)) return parentXPath;
        }

        String relationalXPath = relationalBuilder.synthesizeSiblingAnchorXPath(targetNode, allNodes);
        if (!relationalXPath.isEmpty()) return relationalXPath;
        if ((targetNode.isInputWidget() || targetNode.isCheckboxOrToggleWidget()) && evaluator.isSoleTagInstance(tag, allNodes)) {
            return "//" + tag;
        }
        return targetNode.getXpath();
    }

    public static String toXPathLiteral(String text) {
        if (text == null) return "''";
        if (!text.contains("'")) return "'" + text + "'";
        if (!text.contains("\"")) return "\"" + text + "\"";
        StringBuilder sb = new StringBuilder("concat(");
        String[] parts = text.split("'", -1);
        for (int i = 0; i < parts.length; i++) {
            sb.append("'").append(parts[i]).append("'");
            if (i < parts.length - 1) sb.append(", \"'\", ");
        }
        return sb.append(")").toString();
    }
}
