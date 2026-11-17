package com.qureon.locara.core.generator;

import com.qureon.locara.core.model.UIElementNode;

import java.util.List;

/**
 * Synthesizes relational ancestor, descendant, and sibling-anchored XPaths for
 * React Native container buttons and anonymous inputs/checkboxes without IDs.
 */
final class RelationalXPathBuilder {

    private final XPathUniquenessEvaluator evaluator;

    RelationalXPathBuilder(XPathUniquenessEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    UIElementNode findFirstTextDescendant(UIElementNode container, int maxDepth) {
        if (container == null || maxDepth <= 0) return null;
        for (UIElementNode child : container.getChildren()) {
            if (child.isDisplayed() && child.hasText()) return child;
            UIElementNode nested = findFirstTextDescendant(child, maxDepth - 1);
            if (nested != null) return nested;
        }
        return null;
    }

    UIElementNode findClickableAncestor(UIElementNode node) {
        if (node == null) return null;
        UIElementNode current = node.getParent();
        int levels = 0;
        while (current != null && levels < 4) {
            String cls = current.getClassName();
            String tag = current.getTag();
            if (current.isClickable() && (cls.contains("ViewGroup") || tag.contains("ViewGroup")
                    || cls.contains("Layout") || tag.contains("Layout")
                    || cls.contains("Button") || tag.contains("Button"))) {
                return current;
            }
            current = current.getParent();
            levels++;
        }
        return null;
    }

    String synthesizeSiblingAnchorXPath(UIElementNode targetNode, List<UIElementNode> allNodes) {
        if (targetNode == null || targetNode.getParent() == null) return "";
        String targetTag = targetNode.getTag().isEmpty() ? "*" : targetNode.getTag();
        UIElementNode parent = targetNode.getParent();
        List<UIElementNode> siblings = parent.getChildren();
        int targetIdx = siblings.indexOf(targetNode);
        int sameTagSiblings = 0;
        for (UIElementNode sib : siblings) {
            if (evaluator.matchesTag(targetTag, sib)) sameTagSiblings++;
        }

        if (sameTagSiblings == 1) {
            String uniqueSibling = buildUniqueSiblingAxis(targetNode, targetTag, siblings, targetIdx, allNodes);
            if (!uniqueSibling.isEmpty()) return uniqueSibling;
        } else if (sameTagSiblings > 1 && targetIdx >= 0) {
            String indexedSibling = buildAdjacentMultiSiblingAxis(targetTag, siblings, targetIdx, allNodes);
            if (!indexedSibling.isEmpty()) return indexedSibling;
        }

        return synthesizeNestedInputGroupAnchor(targetNode, parent, targetTag, allNodes);
    }

    private String buildUniqueSiblingAxis(UIElementNode targetNode, String targetTag, List<UIElementNode> siblings,
                                          int targetIdx, List<UIElementNode> allNodes) {
        for (int i = 0; i < siblings.size(); i++) {
            if (i == targetIdx) continue;
            UIElementNode sib = siblings.get(i);
            if (sib.isDisplayed() && sib.hasText() && evaluator.isTextUnique(sib.getText(), allNodes)) {
                String sibTag = sib.getTag().isEmpty() ? "android.widget.TextView" : sib.getTag();
                String axis = (i > targetIdx) ? "following-sibling::" : "preceding-sibling::";
                if (targetNode.isInputWidget() || targetNode.isCheckboxOrToggleWidget()) {
                    return String.format("//%s[%s%s[@text=%s]]", targetTag, axis, sibTag, DynamicXPathGenerator.toXPathLiteral(sib.getText()));
                }
                if (targetNode.isClickable() || targetNode.isCheckable()) {
                    String attr = targetNode.isClickable() ? "@clickable='true'" : "@checkable='true'";
                    return String.format("//%s[%s and %s%s[@text=%s]]", targetTag, attr, axis, sibTag, DynamicXPathGenerator.toXPathLiteral(sib.getText()));
                }
            }
        }
        return "";
    }

    private String buildAdjacentMultiSiblingAxis(String targetTag, List<UIElementNode> siblings,
                                                 int targetIdx, List<UIElementNode> allNodes) {
        if (targetIdx > 0) {
            UIElementNode prevSib = siblings.get(targetIdx - 1);
            if (prevSib.isDisplayed() && prevSib.hasText() && evaluator.isTextUnique(prevSib.getText(), allNodes)) {
                String sibTag = prevSib.getTag().isEmpty() ? "android.widget.TextView" : prevSib.getTag();
                return String.format("//%s[@text=%s]/following-sibling::%s[1]", sibTag, DynamicXPathGenerator.toXPathLiteral(prevSib.getText()), targetTag);
            }
        }
        if (targetIdx + 1 < siblings.size()) {
            UIElementNode nextSib = siblings.get(targetIdx + 1);
            if (nextSib.isDisplayed() && nextSib.hasText() && evaluator.isTextUnique(nextSib.getText(), allNodes)) {
                String sibTag = nextSib.getTag().isEmpty() ? "android.widget.TextView" : nextSib.getTag();
                return String.format("//%s[@text=%s]/preceding-sibling::%s[1]", sibTag, DynamicXPathGenerator.toXPathLiteral(nextSib.getText()), targetTag);
            }
        }
        return "";
    }

    private String synthesizeNestedInputGroupAnchor(UIElementNode targetNode, UIElementNode parent,
                                                    String targetTag, List<UIElementNode> allNodes) {
        if (!targetNode.isInputWidget() || parent.getParent() == null) return "";
        UIElementNode grandParent = parent.getParent();
        int inputsInGrandParent = countDescendantTag(grandParent, targetTag);
        if (inputsInGrandParent == 1) {
            for (UIElementNode aunt : grandParent.getChildren()) {
                if (aunt != parent && aunt.isDisplayed() && aunt.hasText() && evaluator.isTextUnique(aunt.getText(), allNodes)) {
                    String gpTag = grandParent.getTag().isEmpty() ? "android.view.ViewGroup" : grandParent.getTag();
                    String auntTag = aunt.getTag().isEmpty() ? "android.widget.TextView" : aunt.getTag();
                    return String.format("//%s[./%s[@text=%s]]//%s", gpTag, auntTag, DynamicXPathGenerator.toXPathLiteral(aunt.getText()), targetTag);
                }
            }
        } else if (inputsInGrandParent > 1 && countDescendantTag(parent, targetTag) == 1) {
            List<UIElementNode> gpChildren = grandParent.getChildren();
            int parentIdx = gpChildren.indexOf(parent);
            if (parentIdx > 0) {
                UIElementNode prevAunt = gpChildren.get(parentIdx - 1);
                if (prevAunt.isDisplayed() && prevAunt.hasText() && evaluator.isTextUnique(prevAunt.getText(), allNodes)) {
                    String auntTag = prevAunt.getTag().isEmpty() ? "android.widget.TextView" : prevAunt.getTag();
                    String parentTag = parent.getTag().isEmpty() ? "android.view.ViewGroup" : parent.getTag();
                    return String.format("//%s[@text=%s]/following-sibling::%s[1]//%s",
                            auntTag, DynamicXPathGenerator.toXPathLiteral(prevAunt.getText()), parentTag, targetTag);
                }
            }
        }
        return "";
    }

    private int countDescendantTag(UIElementNode container, String tag) {
        if (container == null) return 0;
        int count = 0;
        for (UIElementNode child : container.getChildren()) {
            if (evaluator.matchesTag(tag, child)) count++;
            count += countDescendantTag(child, tag);
        }
        return count;
    }
}
