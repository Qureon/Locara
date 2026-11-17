package com.qureon.locara.core.generator;

import com.qureon.locara.core.matcher.UniversalSemanticDictionary;
import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.UIElementNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Derives standardized Katalon TestObject file paths and entity names following
 * Code Naming Intelligence conventions ({@code Btn*}, {@code Txt*}, {@code Inp*}, {@code Pnl*}, {@code Img*}, {@code Chk*}, {@code Rdo*}).
 */
final class RsSemanticNamingService {

    private static final Pattern UUID_IN_ID_PATTERN = Pattern.compile(".*[0-9a-f]{8}-[0-9a-f]{4}-.*");
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^a-zA-Z0-9]+");
    private static final Pattern NON_SAFE_FILE_PATTERN = Pattern.compile("[^a-zA-Z0-9_\\-]");
    private static final Set<String> NO_EXPANSION_CONTEXT = new HashSet<>(Arrays.asList(
            "rek", "rekening", "hp", "handphone", "telp", "wa", "trx", "transaksi", "kartu", "cif", "pin", "referensi", "ref", "urut"
    ));

    RsSemanticNamingService() {}

    String resolveRsRelativePath(HealContext context, HealCandidate candidate, HealConfig config) {
        if (context != null && !context.getTargetRsPath().isEmpty()) {
            String path = context.getTargetRsPath();
            return path.endsWith(".rs") ? path : path + ".rs";
        }

        String screen = sanitizeFileName(context != null ? context.getScreenName() : "DefaultScreen");
        String objName = "";
        if (candidate != null && candidate.getNode() != null) {
            objName = deriveSemanticObjectName(candidate.getNode(), context != null ? context.getTargetHint() : "");
        } else if (context != null && !context.getTargetHint().isEmpty()) {
            objName = deriveSemanticObjectName(null, context.getTargetHint());
        }
        if (objName.isEmpty()) {
            objName = "HealedElement_" + System.currentTimeMillis();
        }

        String baseFolder = (config != null && config.getDefaultRsFolder() != null && !config.getDefaultRsFolder().trim().isEmpty())
                ? config.getDefaultRsFolder().trim()
                : "Object Repository";
        return baseFolder + "/" + screen + "/" + objName + ".rs";
    }

    String deriveSemanticObjectName(UIElementNode node, String targetHint) {
        if (node == null && (targetHint == null || targetHint.trim().isEmpty())) {
            return "HealedElement_" + System.currentTimeMillis();
        }

        String prefix = deduceWidgetPrefix(node);
        String rawDescriptor = extractRawDescriptor(node, targetHint);
        String pascalDescriptor = toPascalCase(rawDescriptor);
        if (pascalDescriptor.isEmpty()) {
            pascalDescriptor = "Element_" + (node != null ? node.getAttribute("index", "0") : "0");
        }
        if (pascalDescriptor.startsWith(prefix) && pascalDescriptor.length() > prefix.length()) {
            return pascalDescriptor;
        }
        return prefix + pascalDescriptor;
    }

    boolean isCleanResourceId(String resId) {
        return resId != null && !resId.trim().isEmpty() && !UUID_IN_ID_PATTERN.matcher(resId).matches();
    }

    private String extractRawDescriptor(UIElementNode node, String targetHint) {
        if (node != null) {
            if (node.hasContentDesc()) return node.getContentDesc();
            if (node.hasResourceId() && isCleanResourceId(node.getResourceId())) return node.getStrippedResourceId();
            if (node.hasText()) return node.getText();
        }
        return (targetHint != null && !targetHint.trim().isEmpty()) ? targetHint.trim() : "";
    }

    private String deduceWidgetPrefix(UIElementNode node) {
        if (node == null) return "Obj";
        String cls = node.getClassName() != null ? node.getClassName().toLowerCase() : "";
        String resId = node.getResourceId() != null ? node.getResourceId().toLowerCase() : "";

        if (cls.contains("checkbox") || resId.contains("checkbox") || resId.contains("chk")) return "Chk";
        if (cls.contains("radio") || resId.contains("radio") || resId.contains("rdo")) return "Rdo";
        if (cls.contains("edittext") || cls.contains("textinput") || resId.contains("input") || resId.contains("inp")) return "Inp";
        if (cls.contains("image") || resId.contains("art") || resId.contains("img") || resId.contains("icon")) return "Img";
        if (cls.contains("button") || resId.contains("button") || resId.contains("btn")
                || (node.isClickable() && !cls.contains("scrollview") && !cls.contains("layout"))) return "Btn";
        if (cls.contains("textview") || cls.contains("text") || resId.contains("txt")
                || resId.contains("title") || resId.contains("number") || resId.contains("label")) return "Txt";
        if (cls.contains("viewgroup") || cls.contains("layout") || cls.contains("panel")
                || cls.contains("card") || resId.contains("pnl") || resId.contains("card") || resId.contains("screen")) return "Pnl";
        return "Obj";
    }

    private String toPascalCase(String input) {
        if (input == null || input.trim().isEmpty()) return "";
        String[] words = NON_ALNUM_PATTERN.matcher(input).replaceAll(" ").trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.isEmpty()) continue;
            String lower = w.toLowerCase(Locale.ROOT);
            if (UniversalSemanticDictionary.isEstablishedDomainAcronym(lower)) {
                sb.append(w.toUpperCase(Locale.ROOT));
                continue;
            }
            String resolved = w;
            if ("no".equals(lower)) {
                String nextLower = (i + 1 < words.length) ? words[i + 1].toLowerCase(Locale.ROOT) : "";
                if (NO_EXPANSION_CONTEXT.contains(nextLower)) {
                    resolved = "Nomor";
                }
            } else {
                String expanded = UniversalSemanticDictionary.expandCrypticAbbreviation(lower);
                if (expanded != null && !expanded.isEmpty()) {
                    resolved = expanded;
                }
            }
            sb.append(Character.toUpperCase(resolved.charAt(0)));
            if (resolved.length() > 1) {
                sb.append(resolved.substring(1));
            }
        }
        return sb.toString();
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "unnamed";
        return NON_SAFE_FILE_PATTERN.matcher(name).replaceAll("_");
    }
}
