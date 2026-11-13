package com.qureon.locara.cli.audit;

import com.qureon.locara.core.matcher.UniversalSemanticDictionary;
import com.qureon.locara.katalon.variable.AutoVariableSynthesizer;
import com.qureon.locara.katalon.variable.TestCaseVariable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autonomous Code Naming Intelligence & Convention Standardization Auditor.
 * Scans codebases in MODE A (Audit Only) to detect 15 categories of naming flaws
 * and produce structured reports in markdown and console tables.
 */
public class CodeNamingAuditor {

    private static final Set<String> GENERIC_NAMES = new HashSet<>(Arrays.asList(
            "data", "temp", "val", "value", "item", "object", "obj", "result", "x", "abc", "test", "variable", "var"
    ));

    private static final Pattern GROOVY_VAR_DECL = Pattern.compile("(?:def|String|int|long|boolean|Map|List)\\s+([a-zA-Z0-9_]+)\\s*=");
    private static final Pattern RS_ENTITY_NAME = Pattern.compile("<name>([^<]+)</name>");

    private final AutoVariableSynthesizer avs = new AutoVariableSynthesizer();

    /**
     * Executes a full Mode A naming audit across the given project directory.
     */
    public List<CodeNamingFinding> auditProject(File rootDir) {
        List<CodeNamingFinding> findings = new ArrayList<>();
        if (rootDir == null || !rootDir.exists()) {
            return findings;
        }

        scanDirectory(rootDir, rootDir, findings);
        return findings;
    }

    private void scanDirectory(File current, File rootDir, List<CodeNamingFinding> findings) {
        if (current.getName().startsWith(".") || current.getName().equals("build") || current.getName().equals("out")) {
            return;
        }

        File[] files = current.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectory(f, rootDir, findings);
            } else {
                String name = f.getName().toLowerCase();
                String relPath = getRelativePath(rootDir, f);
                try {
                    if (name.endsWith(".rs")) {
                        auditRsFile(f, relPath, findings);
                    } else if (name.endsWith(".tc")) {
                        auditTcFile(f, relPath, findings);
                    } else if (name.endsWith(".groovy")) {
                        auditGroovyFile(f, relPath, findings);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void auditRsFile(File rsFile, String relPath, List<CodeNamingFinding> findings) throws IOException {
        String baseName = rsFile.getName().replace(".rs", "").replace(".healed", "");
        
        // 1. Check for legacy numbers prefix (e.g. "01. ViewGroup - Submit")
        if (baseName.matches("^\\d+\\.\\s*.*")) {
            String cleanName = baseName.replaceFirst("^\\d+\\.\\s*", "");
            findings.add(new CodeNamingFinding(
                    relPath, "Filename", baseName, "TEST_OBJECT",
                    "LEGACY_NUMBERED_PREFIX", "PASCAL_CASE_STANDARDIZED",
                    "Legacy sequential number prefix degrades reusability and locator maintainability",
                    suggestCanonicalRsName(cleanName),
                    "Object Repository entities should be named by responsibility and role, not execution order",
                    "MEDIUM"
            ));
        }

        // 2. Check for generic names (e.g. "01. ViewGroup", "Button_Submit")
        if (baseName.equalsIgnoreCase("ViewGroup") || baseName.equalsIgnoreCase("Button") || baseName.equalsIgnoreCase("TextView")) {
            findings.add(new CodeNamingFinding(
                    relPath, "Filename", baseName, "TEST_OBJECT",
                    "GENERIC_WIDGET_NAME", "CANONICAL_DOMAIN_NAME",
                    "Generic widget class name communicates zero domain purpose or responsibility",
                    "Btn" + rsFile.getParentFile().getName() + "Action",
                    "TestObjects must identify the specific business entity they interact with",
                    "HIGH"
            ));
        }

        // 3. Check for cryptic abbreviations in object name (e.g. "TxtNoRek")
        List<String> crypticRsWords = new ArrayList<>();
        String expandedRs = expandCrypticTokens(splitIdentifierTokens(baseName), false, crypticRsWords);
        if (!crypticRsWords.isEmpty()) {
            findings.add(new CodeNamingFinding(
                    relPath, "Filename", baseName, "TEST_OBJECT",
                    "CRYPTIC_ABBREVIATION", "EXPANDED_DOMAIN_NAME",
                    "Cryptic abbreviation(s) detected: " + String.join(", ", crypticRsWords),
                    expandedRs,
                    "Standardize on unambiguous domain vocabulary",
                    "LOW"
            ));
        }
    }

    private void auditTcFile(File tcFile, String relPath, List<CodeNamingFinding> findings) throws IOException {
        List<TestCaseVariable> vars = avs.parseVariables(tcFile);
        for (TestCaseVariable v : vars) {
            String vName = v.getName();

            // 1. Generic names check
            if (GENERIC_NAMES.contains(vName.toLowerCase(java.util.Locale.ROOT))) {
                findings.add(new CodeNamingFinding(
                        relPath, "Variable: " + vName, vName, "VARIABLE",
                        "GENERIC_IDENTIFIER", "DESCRIPTIVE_DOMAIN_NAME",
                        "Meaningless generic variable name violates Section 4.1 specification",
                        suggestDomainVariableName(vName, relPath, ""),
                        "Variables must communicate their specific financial or behavioral role",
                        "HIGH"
                ));
            }

            // 2. Cryptic abbreviation check (e.g. "noRek", "trxAmt")
            List<String> crypticVarWords = new ArrayList<>();
            String expandedVar = expandCrypticTokens(splitIdentifierTokens(vName), true, crypticVarWords);
            if (!crypticVarWords.isEmpty()) {
                findings.add(new CodeNamingFinding(
                        relPath, "Variable: " + vName, vName, "VARIABLE",
                        "CRYPTIC_ABBREVIATION", "EXPANDED_DOMAIN_NAME",
                        "Cryptic abbreviation(s) detected: " + String.join(", ", crypticVarWords),
                        expandedVar,
                        "Use canonical unambiguous terminology",
                        "MEDIUM"
                ));
            }
        }
    }

    private String expandCrypticTokens(String[] tokens, boolean preserveFirstLowercase, List<String> crypticWordsOut) {
        StringBuilder expandedBuilder = new StringBuilder();
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
            expandedBuilder.append((preserveFirstLowercase && idx == 0) ? tokenOut : capitalize(tokenOut));
        }
        return expandedBuilder.toString();
    }

    private void auditGroovyFile(File groovyFile, String relPath, List<CodeNamingFinding> findings) throws IOException {
        List<String> lines = Files.readAllLines(groovyFile.toPath(), StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) continue;

            Matcher m = GROOVY_VAR_DECL.matcher(line);
            if (m.find()) {
                String varName = m.group(1);
                String nextLine = (i + 1 < lines.size()) ? lines.get(i + 1).trim() : "";
                String contextLine = line + " " + nextLine;
                // Do not false-positive on mathematical/UI spatial coordinates (int x = ...getX())
                if (varName.equalsIgnoreCase("x") && (contextLine.contains("getX()") || contextLine.contains("Location") || line.startsWith("int ") || line.startsWith("double ") || line.startsWith("float "))) {
                    continue;
                }
                if (GENERIC_NAMES.contains(varName.toLowerCase()) && !varName.equals("result")) {
                    findings.add(new CodeNamingFinding(
                            relPath, "Line " + (i + 1), varName, "VARIABLE",
                            "GENERIC_IDENTIFIER", "DESCRIPTIVE_DOMAIN_NAME",
                            "Generic local variable '" + varName + "' obscures data semantics",
                            suggestDomainVariableName(varName, relPath, contextLine),
                            "Local variables should convey purpose and conceptual role",
                            "LOW"
                    ));
                }
            }
        }
    }

    private static final Pattern IDENTIFIER_TOKEN_SPLIT_PATTERN = Pattern.compile(
            "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[^a-zA-Z0-9]+"
    );
    private static final Pattern NON_ALNUM_SPACE_PATTERN = Pattern.compile("[^a-zA-Z0-9]+");
    private static final Pattern WHITESPACE_SPLIT_PATTERN = Pattern.compile("\\s+");
    private static final Set<String> INDONESIAN_NOMOR_NOUNS = Set.of(
            "rek", "rekening", "hp", "telp", "telepon", "kartu", "card", "ref", "referensi", "va", "cif", "pin", "mpin", "otp", "urut", "id"
    );
    private static final java.util.Map<String, String> RS_PREFIX_ALIAS_MAP = java.util.Map.ofEntries(
            java.util.Map.entry("button", "Btn"),
            java.util.Map.entry("btn", "Btn"),
            java.util.Map.entry("textview", "Txt"),
            java.util.Map.entry("txt", "Txt"),
            java.util.Map.entry("text", "Txt"),
            java.util.Map.entry("edittext", "Inp"),
            java.util.Map.entry("textinput", "Inp"),
            java.util.Map.entry("inp", "Inp"),
            java.util.Map.entry("viewgroup", "Pnl"),
            java.util.Map.entry("layout", "Pnl"),
            java.util.Map.entry("linearlayout", "Pnl"),
            java.util.Map.entry("relativelayout", "Pnl"),
            java.util.Map.entry("framelayout", "Pnl"),
            java.util.Map.entry("pnl", "Pnl"),
            java.util.Map.entry("imageview", "Img"),
            java.util.Map.entry("imagebutton", "Img"),
            java.util.Map.entry("img", "Img"),
            java.util.Map.entry("checkbox", "Chk"),
            java.util.Map.entry("chk", "Chk"),
            java.util.Map.entry("radiobutton", "Rdo"),
            java.util.Map.entry("rdo", "Rdo")
    );

    private String[] splitIdentifierTokens(String identifier) {
        if (identifier == null || identifier.isEmpty()) return new String[0];
        return IDENTIFIER_TOKEN_SPLIT_PATTERN.split(identifier);
    }

    private boolean isFalsePositiveNo(String word, String[] tokens, int idx) {
        if (!word.equalsIgnoreCase("no")) return false;
        if (idx + 1 < tokens.length) {
            String next = tokens[idx + 1].toLowerCase(java.util.Locale.ROOT);
            // "no" is Indonesian "nomor" primarily when followed by identity/reference nouns
            return !INDONESIAN_NOMOR_NOUNS.contains(next);
        }
        return true;
    }

    private String suggestCanonicalRsName(String raw) {
        String clean = NON_ALNUM_SPACE_PATTERN.matcher(raw).replaceAll(" ").trim();
        String[] words = WHITESPACE_SPLIT_PATTERN.split(clean);
        if (words.length == 0 || clean.isEmpty()) return "ObjElement";

        String firstLower = words[0].toLowerCase(java.util.Locale.ROOT);
        String prefix = RS_PREFIX_ALIAS_MAP.getOrDefault(firstLower, "Obj");
        int startIndex = RS_PREFIX_ALIAS_MAP.containsKey(firstLower) ? 1 : 0;

        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                sb.append(capitalize(words[i]));
            }
        }
        String res = sb.toString();
        if (res.isEmpty()) {
            res = capitalize(words[0]);
        }
        if (res.startsWith(prefix) && res.length() > prefix.length()) {
            return res;
        }
        return prefix + res;
    }

    private String suggestDomainVariableName(String genericName, String relPath, String codeContext) {
        if (codeContext != null) {
            if (codeContext.contains("getVariable")) return "resolvedVariable";
            if (codeContext.contains("getText")) return "extractedText";
            if (codeContext.contains("getAttribute") || codeContext.contains("safeGetAttribute")) return "attributeValue";
        }
        String base = relPath != null ? relPath.toLowerCase() : "";
        if (base.contains("pulsa") || base.contains("paket")) {
            return "topUpNominal";
        }
        if (base.contains("transfer")) {
            return "transferAmount";
        }
        if (base.contains("rekening") || base.contains("account")) {
            return "accountDetails";
        }
        return genericName + "Value";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1) : "");
    }

    private String getRelativePath(File root, File file) {
        try {
            return root.toPath().relativize(file.toPath()).toString().replace("\\", "/");
        } catch (Exception e) {
            return file.getName();
        }
    }

    /**
     * Formats findings into a GitHub Flavored Markdown report adhering to Section 10.
     */
    public String generateMarkdownReport(List<CodeNamingFinding> findings, String projectName) {
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

        sb.append("\n---\n");
        sb.append("### Recommendations\n");
        sb.append("1. **Critical/High Risk**: Review public API signatures and Test Case variable definitions before renaming.\n");
        sb.append("2. **Medium Risk**: Harmonize legacy numbered Object Repository prefixes to standardized PascalCase.\n");
        sb.append("3. **Mode B Execution**: Execute surgical renaming only after reference analysis and explicit approval.\n");

        return sb.toString();
    }
}
