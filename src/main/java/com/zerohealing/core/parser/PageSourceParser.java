package com.zerohealing.core.parser;

import com.zerohealing.core.model.UIElementNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Rectangle;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance XML parser that transforms Appium/UIAutomator2/XCUITest XML
 * dumps into an indexed hierarchical tree of UIElementNode instances.
 */
public class PageSourceParser {
    private static final Pattern BOUNDS_PATTERN = Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

    public PageSourceParser() {}

    /**
     * Parses the raw XML page source string into an indexed list of UIElementNodes.
     */
    public List<UIElementNode> parse(String xmlSource) throws Exception {
        List<UIElementNode> flattenedNodes = new ArrayList<>();
        if (xmlSource == null || xmlSource.trim().isEmpty()) {
            return flattenedNodes;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Disallow external entity resolution for security and speed
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xmlSource)));
        document.getDocumentElement().normalize();

        Element rootElement = document.getDocumentElement();
        if ("hierarchy".equalsIgnoreCase(rootElement.getTagName()) || "AppiumAUT".equalsIgnoreCase(rootElement.getTagName())) {
            NodeList children = rootElement.getChildNodes();
            java.util.Map<String, Integer> tagCounts = new java.util.HashMap<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    String childTag = childElement.getTagName();
                    int tagIndex = tagCounts.getOrDefault(childTag, 0) + 1;
                    tagCounts.put(childTag, tagIndex);
                    String childXPath = "/" + childTag + "[" + tagIndex + "]";
                    traverse(childElement, null, childXPath, 0, flattenedNodes);
                }
            }
        } else {
            String rootTag = rootElement.getTagName();
            traverse(rootElement, null, "/" + rootTag + "[1]", 0, flattenedNodes);
        }

        return flattenedNodes;
    }

    private void traverse(Element element, UIElementNode parentNode, String nodeXPath, int depth, List<UIElementNode> collector) {
        String tagName = element.getTagName();

        NamedNodeMap rawAttrs = element.getAttributes();
        Map<String, String> attributesMap = new LinkedHashMap<>();
        if (rawAttrs != null) {
            for (int i = 0; i < rawAttrs.getLength(); i++) {
                Node attr = rawAttrs.item(i);
                attributesMap.put(attr.getNodeName(), attr.getNodeValue());
            }
        }

        String resourceId = getAttr(element, "resource-id", "id", "name");
        String text = getAttr(element, "text", "label", "value");
        if (text.isEmpty()) {
            text = getAttr(element, "hint", "placeholder");
        }
        String contentDesc = getAttr(element, "content-desc", "contentDescription", "accessibility-id");
        if (contentDesc.isEmpty() && text.isEmpty()) {
            contentDesc = getAttr(element, "hint", "placeholder");
        }
        String className = getAttr(element, "class", "type");
        if (className.isEmpty()) {
            className = tagName;
        }

        Rectangle bounds = parseBounds(element);
        boolean clickable = Boolean.parseBoolean(getAttr(element, "clickable"));
        boolean displayed = parseBooleanDefaultTrue(getAttr(element, "displayed", "visible"));
        boolean enabled = parseBooleanDefaultTrue(getAttr(element, "enabled"));
        boolean scrollable = Boolean.parseBoolean(getAttr(element, "scrollable"));
        boolean focused = Boolean.parseBoolean(getAttr(element, "focused"));

        // Enrich attributesMap with normalized canonical attributes
        attributesMap.putIfAbsent("class", className);
        attributesMap.putIfAbsent("resource-id", resourceId);
        attributesMap.putIfAbsent("text", text);
        attributesMap.putIfAbsent("content-desc", contentDesc);
        attributesMap.putIfAbsent("clickable", String.valueOf(clickable));
        attributesMap.putIfAbsent("displayed", String.valueOf(displayed));
        attributesMap.putIfAbsent("enabled", String.valueOf(enabled));
        attributesMap.putIfAbsent("scrollable", String.valueOf(scrollable));
        attributesMap.putIfAbsent("focused", String.valueOf(focused));

        UIElementNode node = new UIElementNode(
                tagName,
                resourceId,
                text,
                contentDesc,
                className,
                nodeXPath,
                bounds,
                clickable,
                displayed,
                enabled,
                scrollable,
                focused,
                depth,
                attributesMap
        );

        if (parentNode != null) {
            parentNode.addChild(node);
        }

        collector.add(node);

        // Traverse child nodes with sibling-aware XPath indexing
        NodeList children = element.getChildNodes();
        java.util.Map<String, Integer> siblingTagCounts = new java.util.HashMap<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String childTag = childElement.getTagName();
                int tagIndex = siblingTagCounts.getOrDefault(childTag, 0) + 1;
                siblingTagCounts.put(childTag, tagIndex);

                String childXPath = nodeXPath + "/" + childTag + "[" + tagIndex + "]";
                traverse(childElement, node, childXPath, depth + 1, collector);
            }
        }
    }

    private Rectangle parseBounds(Element element) {
        String boundsStr = element.getAttribute("bounds");
        if (boundsStr != null && !boundsStr.isEmpty()) {
            Matcher m = BOUNDS_PATTERN.matcher(boundsStr);
            if (m.find()) {
                int x1 = Integer.parseInt(m.group(1));
                int y1 = Integer.parseInt(m.group(2));
                int x2 = Integer.parseInt(m.group(3));
                int y2 = Integer.parseInt(m.group(4));
                return new Rectangle(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
            }
        }

        // iOS/Web standard: x, y, width, height attributes
        try {
            String xStr = element.getAttribute("x");
            String yStr = element.getAttribute("y");
            String wStr = element.getAttribute("width");
            String hStr = element.getAttribute("height");
            if (!xStr.isEmpty() && !yStr.isEmpty() && !wStr.isEmpty() && !hStr.isEmpty()) {
                int x = (int) Double.parseDouble(xStr);
                int y = (int) Double.parseDouble(yStr);
                int w = (int) Double.parseDouble(wStr);
                int h = (int) Double.parseDouble(hStr);
                return new Rectangle(x, y, w, h);
            }
        } catch (NumberFormatException ignored) {
        }

        return new Rectangle(0, 0, 0, 0);
    }

    private String getAttr(Element el, String... names) {
        for (String name : names) {
            if (el.hasAttribute(name)) {
                String val = el.getAttribute(name);
                if (val != null && !val.trim().isEmpty()) {
                    return val.trim();
                }
            }
        }
        return "";
    }

    private boolean parseBooleanDefaultTrue(String val) {
        if (val == null || val.isEmpty()) return true;
        return !"false".equalsIgnoreCase(val);
    }
}
