package com.qureon.locara.cli.audit;

import com.qureon.locara.core.matcher.UniversalSemanticDictionary;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure naming tokenization, cryptic-abbreviation expansion, canonical Object Repository
 * name synthesis, and Markdown report rendering helpers for {@link CodeNamingAuditor}.
 */
final class CodeNamingRuleHelpers {

    private static final Pattern IDENTIFIER_TOKEN_SPLIT_PATTERN = Pattern.compile(
            "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[^a-zA-Z0-9]+"
    );
    private static final Pattern NON_ALNUM_SPACE_PATTERN = Pattern.compile("[^a-zA-Z0-9]+");
    private static final Pattern WHITESPACE_SPLIT_PATTERN = Pattern.compile("\\s+");
    private static final Set<String> INDONESIAN_NOMOR_NOUNS = Set.of(
            "rek", "rekening", "hp", "telp", "telepon", "kartu", "card", "ref", "referensi", "va", "cif", "pin", "mpin", "otp", "urut", "id"
    );
    private static final Map<String, String> RS_PREFIX_ALIAS_MAP = Map.ofEntries(
            Map.entry("button", "Btn"), Map.entry("btn", "Btn"),
            Map.entry("textview", "Txt"), Map.entry("txt", "Txt"), Map.entry("text", "Txt"),
            Map.entry("edittext", "Inp"), Map.entry("textinput", "Inp"), Map.entry("inp", "Inp"),
            Map.entry("viewgroup", "Pnl"), Map.entry("layout", "Pnl"),
            Map.entry("linearlayout", "Pnl"), Map.entry("relativelayout", "Pnl"),
            Map.entry("framelayout", "Pnl"), Map.entry("pnl", "Pnl"),
            Map.entry("imageview", "Img"), Map.entry("imagebutton", "Img"), Map.entry("img", "Img"),
            Map.entry("checkbox", "Chk"), Map.entry("chk", "Chk"),
            Map.entry("radiobutton", "Rdo"), Map.entry("rdo", "Rdo")
    );

    private CodeNamingRuleHelpers() {}

    static String[] splitIdentifierTokens(String identifier) {
        if (identifier == null || identifier.isEmpty()) return new String[0];
        return IDENTIFIER_TOKEN_SPLIT_PATTERN.split(identifier);
    }

    static String expandCrypticTokens(String[] tokens, boolean preserveFirstLowercase, List<String> crypticWordsOut) {
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < tokens.length; idx++) {
            String word = tokens[idx];
            String tokenOut = word;
            if (!isFalsePositiveNo(word, tokens, idx)) {
                String expanded = UniversalSemanticDictionary.expandCrypticAbbreviation(word);
                if (expanded != null) {
                    crypticWordsOut.add(word + " -> " + expanded);
                    tokenOut = expanded;
                }
            }
            sb.append((preserveFirstLowercase && idx == 0) ? tokenOut : capitalize(tokenOut));
        }
        return sb.toString();
    }

    static boolean isFalsePositiveNo(String word, String[] tokens, int idx) {
        if (!word.equalsIgnoreCase("no")) return false;
        if (idx + 1 < tokens.length) {
            return !INDONESIAN_NOMOR_NOUNS.contains(tokens[idx + 1].toLowerCase(Locale.ROOT));
        }
        return true;
    }

    static String suggestCanonicalRsName(String raw) {
        String clean = NON_ALNUM_SPACE_PATTERN.matcher(raw).replaceAll(" ").trim();
        String[] words = WHITESPACE_SPLIT_PATTERN.split(clean);
        if (words.length == 0 || clean.isEmpty()) return "ObjElement";

        String firstLower = words[0].toLowerCase(Locale.ROOT);
        String prefix = RS_PREFIX_ALIAS_MAP.getOrDefault(firstLower, "Obj");
        int startIndex = RS_PREFIX_ALIAS_MAP.containsKey(firstLower) ? 1 : 0;

        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < words.length; i++) {
            if (!words[i].isEmpty()) sb.append(capitalize(words[i]));
        }
        String res = sb.length() > 0 ? sb.toString() : capitalize(words[0]);
        return (res.startsWith(prefix) && res.length() > prefix.length()) ? res : prefix + res;
    }

    static String suggestDomainVariableName(String genericName, String relPath, String codeContext) {
        if (codeContext != null) {
            if (codeContext.contains("getVariable")) return "resolvedVariable";
            if (codeContext.contains("getText")) return "extractedText";
            if (codeContext.contains("getAttribute") || codeContext.contains("safeGetAttribute")) return "attributeValue";
        }
        String base = relPath != null ? relPath.toLowerCase(Locale.ROOT) : "";
        if (base.contains("pulsa") || base.contains("paket")) return "topUpNominal";
        if (base.contains("transfer")) return "transferAmount";
        if (base.contains("rekening") || base.contains("account")) return "accountDetails";
        return genericName + "Value";
    }

    static String renderMarkdownReport(List<CodeNamingFinding> findings, String projectName) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Code Naming Intelligence Audit Report: ").append(projectName).append("\n\n");
        sb.append("> **Auditor Engine**: Locara by Qureon (Code Naming Intelligence v1.0)\n");
        sb.append("> **Audit Mode**: `MODE A — AUDIT ONLY (Read-Only)`\n");
        sb.append("> **Total Findings**: ").append(findings.size()).append("\n\n");

        if (findings.isEmpty()) {
            sb.append("✅ **Clean Bill of Health**: No naming convention violations detected across scanned entities!\n");
            return sb.toString();
        }
        sb.append("## Naming Violation Inventory\n\n");
        sb.append("| File | Location | Identifier | Category | Current Style | Expected Standard | Issue | Proposed Name | Risk |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|\n");
        for (CodeNamingFinding f : findings) {
            sb.append(String.format("| `%s` | %s | `%s` | %s | %s | %s | %s | `%s` | **%s** |\n",
                    f.getFile(), f.getLocation(), f.getIdentifier(), f.getCategory(),
                    f.getCurrentConvention(), f.getExpectedConvention(), f.getIssue(),
                    f.getProposedName(), f.getRisk()));
        }
        sb.append("\n---\n### Recommendations\n");
        sb.append("1. **Critical/High Risk**: Review public API signatures and Test Case variable definitions before renaming.\n");
        sb.append("2. **Medium Risk**: Harmonize legacy numbered Object Repository prefixes to standardized PascalCase.\n");
        sb.append("3. **Mode B Execution**: Execute surgical renaming only after reference analysis and explicit approval.\n");
        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1) : "");
    }
}
