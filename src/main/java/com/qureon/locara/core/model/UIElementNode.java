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
