package com.qureon.locara.katalon;

import com.qureon.locara.core.matcher.HeuristicMatcher;
import com.qureon.locara.core.model.HealContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts semantic hints, Repository object IDs, locator expressions, and expected tags
 * from Katalon {@code TestObject} instances via zero-dependency reflection.
 */
final class KatalonObjectExtractor {

    private static final Pattern LOCATOR_ATTR_PATTERN =
            Pattern.compile("@(?:text|content-desc)\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern EXPECTED_TAG_PATTERN = Pattern.compile(
            "^(?:[0-9]+(?:-[0-9]+)?\\.\\s*)?(ViewGroup|TextView|Button|EditText|ImageView|ImageButton|CheckBox|RadioButton|View)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> GENERIC_HINT_WORDS = new HashSet<>(Arrays.asList(
            "button", "tombol", "viewgroup", "groupview", "textview", "edittext",
            "imageview", "checkbox", "radiobutton", "view", "input", "field",
            "text", "value", "label"
    ));

    private KatalonObjectExtractor() {}

    static HealContext buildHealContext(Object testObject,
                                      String hint,
                                      String action,
                                      String value,
                                      String explicitRsPath) {
        String rsPath = explicitRsPath != null ? explicitRsPath : extractObjectId(testObject);
        String origLocator = extractLocator(testObject);
        String rawHint = (hint != null && !hint.isEmpty()) ? hint : rsPath;
        String effectiveHint = resolveEffectiveHint(testObject, rawHint, origLocator);

        return HealContext.builder()
                .targetHint(effectiveHint)
                .action(action)
                .value(value)
                .targetRsPath(rsPath)
                .screenName(deriveScreenName(rsPath))
                .originalLocator(origLocator)
                .expectedTag(extractExpectedTag(rsPath))
                .build();
    }

    static String extractObjectId(Object testObject) {
        if (testObject == null) {
            return "";
        }
        try {
            Method getId = testObject.getClass().getMethod("getObjectId");
            Object id = getId.invoke(testObject);
            return id != null ? id.toString() : "";
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    static String extractLocator(Object testObject) {
        if (testObject == null) {
            return "";
        }
        for (String methodName : new String[]{"getMobileLocator", "getLocator"}) {
            try {
                Method m = testObject.getClass().getMethod(methodName);
                Object val = m.invoke(testObject);
                if (val != null && !val.toString().trim().isEmpty()) {
                    return val.toString().trim();
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        try {
            Method getXpath = testObject.getClass().getMethod("findPropertyValue", String.class, boolean.class);
            Object val = getXpath.invoke(testObject, "xpath", false);
            return val != null ? val.toString() : "";
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    static String resolveEffectiveHint(Object testObject, String rawHint, String originalLocator) {
        String cleaned = HeuristicMatcher.cleanSemanticHint(rawHint);
        if (!isGenericOrEmptyHint(cleaned)) {
            return cleaned;
        }
        if (testObject != null) {
            for (String propName : new String[]{"text", "content-desc", "resource-id"}) {
                try {
                    Method findProp = testObject.getClass().getMethod("findPropertyValue", String.class, boolean.class);
                    Object val = findProp.invoke(testObject, propName, false);
                    if (val != null && !val.toString().trim().isEmpty()) {
                        String propVal = val.toString().trim();
                        if ("resource-id".equals(propName) && propVal.contains(":id/")) {
                            propVal = propVal.substring(propVal.indexOf(":id/") + 4);
                        }
                        if (!isGenericOrEmptyHint(propVal)) {
                            return propVal;
                        }
                    }
                } catch (ReflectiveOperationException ignored) {}
            }
        }
        if (originalLocator != null && !originalLocator.isEmpty()) {
            Matcher m = LOCATOR_ATTR_PATTERN.matcher(originalLocator);
            if (m.find()) {
                String extracted = m.group(1).trim();
                if (!isGenericOrEmptyHint(extracted)) {
                    return extracted;
                }
            }
        }
        return !cleaned.isEmpty() ? cleaned : (rawHint != null ? rawHint : "");
    }

    static String extractExpectedTag(String rsPath) {
        if (rsPath == null || rsPath.isEmpty()) {
            return null;
        }
        String name = rsPath.replace("\\", "/");
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        Matcher matcher = EXPECTED_TAG_PATTERN.matcher(name);
        return matcher.find() ? matcher.group(1) : null;
    }

    static String deriveScreenName(String rsPath) {
        if (rsPath == null || rsPath.isEmpty()) {
            return "UnknownScreen";
        }
        String normalized = rsPath.replace("\\", "/");
        if (normalized.contains("Object Repository/")) {
            normalized = normalized.substring(normalized.indexOf("Object Repository/") + 18);
        }
        return normalized.contains("/") ? normalized.substring(0, normalized.indexOf("/")) : "DefaultScreen";
    }

    private static boolean isGenericOrEmptyHint(String value) {
        return value == null || value.trim().isEmpty()
                || GENERIC_HINT_WORDS.contains(value.trim().toLowerCase());
    }
}
