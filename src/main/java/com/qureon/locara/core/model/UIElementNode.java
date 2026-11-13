package com.qureon.locara.core.model;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable/Rich domain representation of a mobile UI element extracted from Appium/UIAutomator2/XCUITest XML.
 */
public class UIElementNode {
    private final String tag;
    private final String resourceId;
    private final String text;
    private final String contentDesc;
    private final String className;
    private final String xpath;
    private final Rectangle bounds;
    private final boolean clickable;
    private final boolean displayed;
    private final boolean enabled;
    private final boolean scrollable;
    private final boolean focused;
    private final int depth;
    private final Map<String, String> attributes;

    private UIElementNode parent;
    private final List<UIElementNode> children = new ArrayList<>();

    public UIElementNode(String tag,
                         String resourceId,
                         String text,
                         String contentDesc,
                         String className,
                         String xpath,
                         Rectangle bounds,
                         boolean clickable,
                         boolean displayed,
                         boolean enabled,
                         boolean scrollable,
                         boolean focused,
                         int depth) {
        this(tag, resourceId, text, contentDesc, className, xpath, bounds,
                clickable, displayed, enabled, scrollable, focused, depth, Collections.emptyMap());
    }

    public UIElementNode(String tag,
                         String resourceId,
                         String text,
                         String contentDesc,
                         String className,
                         String xpath,
                         Rectangle bounds,
                         boolean clickable,
                         boolean displayed,
                         boolean enabled,
                         boolean scrollable,
                         boolean focused,
                         int depth,
                         Map<String, String> attributes) {
        this.tag = tag != null ? tag : "";
        this.resourceId = resourceId != null ? resourceId : "";
        this.text = text != null ? text.trim() : "";
        this.contentDesc = contentDesc != null ? contentDesc.trim() : "";
        this.className = className != null ? className : "";
        this.xpath = xpath != null ? xpath : "";
        this.bounds = bounds != null ? bounds : new Rectangle(0, 0, 0, 0);
        this.clickable = clickable;
        this.displayed = displayed;
        this.enabled = enabled;
        this.scrollable = scrollable;
        this.focused = focused;
        this.depth = depth;
        this.attributes = attributes != null ? Collections.unmodifiableMap(new LinkedHashMap<>(attributes)) : Collections.emptyMap();
    }

    public void addChild(UIElementNode child) {
        if (child != null) {
            child.parent = this;
            this.children.add(child);
        }
    }

    public String getTag() { return tag; }
    public String getResourceId() { return resourceId; }
    public String getText() { return text; }
    public String getContentDesc() { return contentDesc; }
    public String getClassName() { return className; }
    public String getXpath() { return xpath; }
    public Rectangle getBounds() { return bounds; }
    public boolean isClickable() { return clickable; }
    public boolean isDisplayed() { return displayed; }
    public boolean isEnabled() { return enabled; }
    public boolean isScrollable() { return scrollable; }
    public boolean isFocused() { return focused; }
    public int getDepth() { return depth; }
    public UIElementNode getParent() { return parent; }

    public boolean hasClickableAncestor() {
        return hasClickableAncestor(4);
    }

    public boolean hasClickableAncestor(int maxDepth) {
        UIElementNode current = this.parent;
        int levels = 0;
        while (current != null && levels < maxDepth) {
            if (current.isClickable()) return true;
            current = current.getParent();
            levels++;
        }
        return false;
    }
    public List<UIElementNode> getChildren() { return Collections.unmodifiableList(children); }

    public int getCenterX() {
        return bounds.x + (bounds.width / 2);
    }

    public int getCenterY() {
        return bounds.y + (bounds.height / 2);
    }

    public boolean hasText() {
        return !text.isEmpty();
    }

    public boolean hasResourceId() {
        return !resourceId.isEmpty();
    }

    public boolean hasContentDesc() {
        return !contentDesc.isEmpty();
    }

    public String getStrippedResourceId() {
        if (resourceId.contains(":id/")) {
            return resourceId.substring(resourceId.indexOf(":id/") + 4);
        }
        if (resourceId.contains("/")) {
            return resourceId.substring(resourceId.lastIndexOf('/') + 1);
        }
        return resourceId;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getAttribute(String key) {
        return getAttribute(key, "");
    }

    public String getAttribute(String key, String defaultValue) {
        return attributes.getOrDefault(key, defaultValue);
    }

    public String getPackage() {
        return getAttribute("package", "");
    }

    public String getBoundsString() {
        if (attributes.containsKey("bounds") && !attributes.get("bounds").isEmpty()) {
            return attributes.get("bounds");
        }
        return "[" + bounds.x + "," + bounds.y + "][" + (bounds.x + bounds.width) + "," + (bounds.y + bounds.height) + "]";
    }

    public boolean isCheckable() {
        return Boolean.parseBoolean(getAttribute("checkable", "false"));
    }

    public boolean isChecked() {
        return Boolean.parseBoolean(getAttribute("checked", "false"));
    }

    public boolean isInputWidget() {
        String t = tag.toLowerCase();
        String c = className.toLowerCase();
        return t.contains("edittext") || c.contains("edittext") ||
               t.contains("textfield") || c.contains("textfield") ||
               t.contains("autocompletetextview") || c.contains("autocompletetextview") ||
               "input".equals(t) || "textarea".equals(t);
    }

    public boolean isCheckboxOrToggleWidget() {
        String t = tag.toLowerCase();
        String c = className.toLowerCase();
        return isCheckable() ||
               t.contains("checkbox") || c.contains("checkbox") ||
               t.contains("radiobutton") || c.contains("radiobutton") ||
               t.contains("switch") || c.contains("switch") ||
               t.contains("togglebutton") || c.contains("togglebutton") ||
               "checkbox".equalsIgnoreCase(getAttribute("type")) ||
               "checkbox".equalsIgnoreCase(getAttribute("role"));
    }

    /**
     * Detects compact, square-like interactive ViewGroups/Views/ImageViews (<= 160px)
     * frequently used by React Native & Flutter developers as custom checkboxes without resource-id.
     */
    public boolean isCompactCheckboxGeometry() {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return false;
        if (bounds.width > 160 || bounds.height > 160) return false;
        if (Math.abs(bounds.width - bounds.height) > 45) return false;
        String t = tag.toLowerCase();
        String c = className.toLowerCase();
        boolean isContainerOrIcon = t.contains("viewgroup") || c.contains("viewgroup") ||
                                    t.contains("view") || c.contains("view") ||
                                    t.contains("imageview") || c.contains("imageview");
        return isContainerOrIcon && (clickable || isCheckable() || (parent != null && parent.isClickable()));
    }

    /**
     * Aggregates visible text or content-desc from direct/nested children (up to maxDepth)
     * when a clickable container (e.g. React Native ViewGroup) has no direct text attribute of its own.
     */
    public String getAggregatedDescendantText(int maxDepth) {
        if (hasText()) return text;
        StringBuilder sb = new StringBuilder();
        collectDescendantText(this, 0, maxDepth, sb);
        return sb.toString().trim();
    }

    private void collectDescendantText(UIElementNode current, int currentDepth, int maxDepth, StringBuilder sb) {
        if (current == null || currentDepth > maxDepth) return;
        if (current != this) {
            if (current.hasText()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(current.getText());
            } else if (current.hasContentDesc()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(current.getContentDesc());
            }
        }
        if (currentDepth < maxDepth) {
            for (UIElementNode child : current.getChildren()) {
                if (child.isDisplayed()) {
                    collectDescendantText(child, currentDepth + 1, maxDepth, sb);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "UIElementNode{" +
                "tag='" + tag + '\'' +
                ", id='" + getStrippedResourceId() + '\'' +
                ", text='" + text + '\'' +
                ", desc='" + contentDesc + '\'' +
                ", bounds=" + bounds.x + "," + bounds.y + "," + bounds.width + "x" + bounds.height +
                ", clickable=" + clickable +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UIElementNode that)) return false;
        return clickable == that.clickable &&
                displayed == that.displayed &&
                enabled == that.enabled &&
                Objects.equals(tag, that.tag) &&
                Objects.equals(resourceId, that.resourceId) &&
                Objects.equals(text, that.text) &&
                Objects.equals(contentDesc, that.contentDesc) &&
                Objects.equals(bounds, that.bounds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, resourceId, text, contentDesc, bounds, clickable, displayed, enabled);
    }
}
