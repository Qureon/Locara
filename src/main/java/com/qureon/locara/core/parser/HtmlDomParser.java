package com.qureon.locara.core.parser;

import com.qureon.locara.core.model.UIElementNode;

import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, zero-dependency HTML DOM parser tailored for mobile hybrid WebViews
 * (Cordova, Capacitor, Ionic, React Native WebViews, and browser-based flows).
 *
 * Transforms HTML DOM into standard {@link UIElementNode} trees, extracting
 * ARIA accessibility semantics, standard HTML5 attributes, and test-tags.
 */
public class HtmlDomParser {

    private static final Set<String> VOID_TAGS = new HashSet<>(Arrays.asList(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
    ));

    private static final Set<String> CLICKABLE_TAGS = new HashSet<>(Arrays.asList(
            "button", "a", "select", "option"
    ));

    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script\\b[^>]*>.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_PATTERN = Pattern.compile("<style\\b[^>]*>.*?</style>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern ATTR_PATTERN = Pattern.compile("([a-zA-Z0-9_:-]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?");
    private static final Pattern TAG_PATTERN = Pattern.compile("</?([a-zA-Z0-9:-]+)((?:\\s+[^>/]+)*)\\s*(/?)>", Pattern.DOTALL);

    public HtmlDomParser() {}

    /**
     * Parses a raw HTML string into a flattened, indexed list of UIElementNodes.
     */
    public List<UIElementNode> parse(String htmlSource) {
        List<UIElementNode> flattenedNodes = new ArrayList<>();
        if (htmlSource == null || htmlSource.trim().isEmpty()) {
            return flattenedNodes;
        }

        // 1. Sanitize HTML
        String sanitized = COMMENT_PATTERN.matcher(htmlSource).replaceAll("");
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = STYLE_PATTERN.matcher(sanitized).replaceAll("");

        // 2. Tokenize tags and construct elements
        Matcher matcher = TAG_PATTERN.matcher(sanitized);
        Deque<HtmlElementBuilder> stack = new ArrayDeque<>();
        Map<String, Integer> globalTagCounts = new HashMap<>();

        int lastEnd = 0;

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            // Text between previous tag end and current tag start
            if (start > lastEnd && !stack.isEmpty()) {
                String textSegment = sanitized.substring(lastEnd, start).trim();
                if (!textSegment.isEmpty()) {
                    stack.peek().appendText(textSegment);
                }
            }
            lastEnd = end;

            String fullMatch = matcher.group(0);
            boolean isClosing = fullMatch.startsWith("</");
            String tagName = matcher.group(1).toLowerCase();
            String rawAttrs = matcher.group(2);
            boolean selfClosing = "/".equals(matcher.group(3)) || VOID_TAGS.contains(tagName);

            if (isClosing) {
                // Pop stack until matching tag found
                while (!stack.isEmpty()) {
                    HtmlElementBuilder popped = stack.pop();
                    if (popped.tagName.equalsIgnoreCase(tagName)) {
                        UIElementNode node = popped.build();
                        flattenedNodes.add(node);
                        break;
                    } else {
                        // Tag was not explicitly closed in HTML; still record it
                        UIElementNode unclosed = popped.build();
                        flattenedNodes.add(unclosed);
                    }
                }
            } else {
                // Opening tag
                Map<String, String> attributes = parseAttributes(rawAttrs);
                int depth = stack.size() + 1;

                // Compute XPath index
                int tagIndex = globalTagCounts.getOrDefault(tagName, 0) + 1;
                globalTagCounts.put(tagName, tagIndex);

                String parentXpath = stack.isEmpty() ? "" : stack.peek().xpath;
                String currentXpath;
                String idAttr = attributes.get("id");
                if (idAttr != null && !idAttr.trim().isEmpty()) {
                    currentXpath = "//" + tagName + "[@id='" + idAttr.trim() + "']";
                } else {
                    currentXpath = parentXpath + "/" + tagName + "[" + tagIndex + "]";
                }

                HtmlElementBuilder builder = new HtmlElementBuilder(tagName, attributes, currentXpath, depth);

                if (selfClosing) {
                    UIElementNode node = builder.build();
                    flattenedNodes.add(node);
                    if (!stack.isEmpty()) {
                        stack.peek().addChild(node);
                    }
                } else {
                    if (!stack.isEmpty()) {
                        stack.peek().addChildBuilder(builder);
                    }
                    stack.push(builder);
                }
            }
        }

        // Flush remaining unclosed tags
        while (!stack.isEmpty()) {
            flattenedNodes.add(stack.pop().build());
        }

        return flattenedNodes;
    }

    private Map<String, String> parseAttributes(String rawAttrs) {
        if (rawAttrs == null || rawAttrs.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>();
        Matcher attrMatcher = ATTR_PATTERN.matcher(rawAttrs);
        while (attrMatcher.find()) {
            String name = attrMatcher.group(1).toLowerCase();
            String val = attrMatcher.group(2);
            if (val == null) val = attrMatcher.group(3);
            if (val == null) val = attrMatcher.group(4);
            if (val == null) val = "";
            map.put(name, val);
        }
        return map;
    }

    private static class HtmlElementBuilder {
        final String tagName;
        final Map<String, String> attributes;
        final String xpath;
        final int depth;
        final StringBuilder textContent = new StringBuilder();
        final List<UIElementNode> builtChildren = new ArrayList<>();

        HtmlElementBuilder(String tagName, Map<String, String> attributes, String xpath, int depth) {
            this.tagName = tagName;
            this.attributes = attributes;
            this.xpath = xpath;
            this.depth = depth;
        }

        void appendText(String text) {
            if (textContent.length() > 0) {
                textContent.append(" ");
            }
            textContent.append(text);
        }

        void addChild(UIElementNode child) {
            builtChildren.add(child);
        }

        void addChildBuilder(HtmlElementBuilder childBuilder) {
            // Placeholder for tree structure tracking
        }

        UIElementNode build() {
            String id = attributes.getOrDefault("id", attributes.getOrDefault("name", ""));
            String className = attributes.getOrDefault("class", "");
            
            // Priority for visible text: innerText > placeholder > value > title
            String text = textContent.toString().trim();
            if (text.isEmpty() && attributes.containsKey("placeholder")) {
                text = attributes.get("placeholder").trim();
            }
            if (text.isEmpty() && attributes.containsKey("value")) {
                text = attributes.get("value").trim();
            }

            // Priority for content-desc: aria-label > data-testid > data-test > data-cy > title
            String contentDesc = attributes.getOrDefault("aria-label", "");
            if (contentDesc.isEmpty()) {
                contentDesc = attributes.getOrDefault("data-testid", "");
            }
            if (contentDesc.isEmpty()) {
                contentDesc = attributes.getOrDefault("data-test", "");
            }
            if (contentDesc.isEmpty()) {
                contentDesc = attributes.getOrDefault("data-cy", "");
            }
            if (contentDesc.isEmpty()) {
                contentDesc = attributes.getOrDefault("data-qa", "");
            }
            if (contentDesc.isEmpty()) {
                contentDesc = attributes.getOrDefault("title", "");
            }

            // Clickable heuristics
            String role = attributes.getOrDefault("role", "").toLowerCase();
            String type = attributes.getOrDefault("type", "").toLowerCase();
            boolean isClickable = CLICKABLE_TAGS.contains(tagName)
                    || "button".equals(role)
                    || "link".equals(role)
                    || attributes.containsKey("onclick")
                    || ("input".equals(tagName) && Arrays.asList("button", "submit", "reset", "checkbox", "radio").contains(type));

            // Displayed heuristics
            String style = attributes.getOrDefault("style", "").toLowerCase();
            boolean isDisplayed = !style.contains("display:none")
                    && !style.contains("display: none")
                    && !style.contains("visibility:hidden")
                    && !style.contains("visibility: hidden")
                    && !"hidden".equals(type);

            // Enabled heuristics
            boolean isEnabled = !attributes.containsKey("disabled") && !"true".equals(attributes.get("aria-disabled"));

            UIElementNode node = new UIElementNode(
                    tagName,
                    id,
                    text,
                    contentDesc,
                    className,
                    xpath,
                    new Rectangle(0, 0, 0, 0),
                    isClickable,
                    isDisplayed,
                    isEnabled,
                    "div".equals(tagName) || "body".equals(tagName) || "section".equals(tagName),
                    false,
                    depth,
                    attributes
            );

            for (UIElementNode child : builtChildren) {
                node.addChild(child);
            }

            return node;
        }
    }
}
