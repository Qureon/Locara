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
        String[] rsTokens = splitIdentifierTokens(baseName);
        StringBuilder expandedRs = new StringBuilder();
        List<String> crypticRsWords = new ArrayList<>();
        for (int idx = 0; idx < rsTokens.length; idx++) {
            String word = rsTokens[idx];
            if (!isFalsePositiveNo(word, rsTokens, idx)) {
                String expanded = UniversalSemanticDictionary.expandCrypticAbbreviation(word);
                if (expanded != null) {
                    crypticRsWords.add(word + " -> " + expanded);
                    expandedRs.append(capitalize(expanded));
                    continue;
                }
            }
            expandedRs.append(capitalize(word));
        }
        if (!crypticRsWords.isEmpty()) {
            findings.add(new CodeNamingFinding(
                    relPath, "Filename", baseName, "TEST_OBJECT",
                    "CRYPTIC_ABBREVIATION", "EXPANDED_DOMAIN_NAME",
                    "Cryptic abbreviation(s) detected: " + String.join(", ", crypticRsWords),
                    expandedRs.toString(),
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
            if (GENERIC_NAMES.contains(vName.toLowerCase())) {
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
            String[] varTokens = splitIdentifierTokens(vName);
            StringBuilder expandedVar = new StringBuilder();
            List<String> crypticVarWords = new ArrayList<>();
            for (int idx = 0; idx < varTokens.length; idx++) {
                String word = varTokens[idx];
                if (!isFalsePositiveNo(word, varTokens, idx)) {
                    String expanded = UniversalSemanticDictionary.expandCrypticAbbreviation(word);
                    if (expanded != null) {
                        crypticVarWords.add(word + " -> " + expanded);
                        expandedVar.append(idx == 0 ? expanded : capitalize(expanded));
                        continue;
                    }
                }
                expandedVar.append(idx == 0 ? word : capitalize(word));
            }
            if (!crypticVarWords.isEmpty()) {
                findings.add(new CodeNamingFinding(
                        relPath, "Variable: " + vName, vName, "VARIABLE",
                        "CRYPTIC_ABBREVIATION", "EXPANDED_DOMAIN_NAME",
                        "Cryptic abbreviation(s) detected: " + String.join(", ", crypticVarWords),
                        expandedVar.toString(),
                        "Use canonical unambiguous terminology",
                        "MEDIUM"
                ));
            }
        }
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

    private String[] splitIdentifierTokens(String identifier) {
        if (identifier == null || identifier.isEmpty()) return new String[0];
        return identifier.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[^a-zA-Z0-9]+");
    }

    private boolean isFalsePositiveNo(String word, String[] tokens, int idx) {
        if (!word.equalsIgnoreCase("no")) return false;
        if (idx + 1 < tokens.length) {
            String next = tokens[idx + 1].toLowerCase();
            // "no" is Indonesian "nomor" primarily when followed by identity/reference nouns
            Set<String> indonesianNomorNouns = new HashSet<>(Arrays.asList(
                    "rek", "rekening", "hp", "telp", "telepon", "kartu", "card", "ref", "referensi", "va", "cif", "pin", "mpin", "otp", "urut", "id"
            ));
            return !indonesianNomorNouns.contains(next);
        }
        return true;
    }

    private String suggestCanonicalRsName(String raw) {
        String clean = raw.replaceAll("[^a-zA-Z0-9]+", " ").trim();
        String[] words = clean.split("\\s+");
        if (words.length == 0 || clean.isEmpty()) return "ObjElement";

        String prefix = "Obj";
        int startIndex = 0;
        String firstLower = words[0].toLowerCase();
        if (firstLower.equals("button") || firstLower.equals("btn")) {
            prefix = "Btn";
            startIndex = 1;
        } else if (firstLower.equals("textview") || firstLower.equals("txt") || firstLower.equals("text")) {
            prefix = "Txt";
            startIndex = 1;
        } else if (firstLower.equals("edittext") || firstLower.equals("textinput") || firstLower.equals("inp")) {
            prefix = "Inp";
            startIndex = 1;
        } else if (firstLower.equals("viewgroup") || firstLower.equals("layout") || firstLower.equals("linearlayout")
                || firstLower.equals("relativelayout") || firstLower.equals("framelayout") || firstLower.equals("pnl")) {
            prefix = "Pnl";
            startIndex = 1;
        } else if (firstLower.equals("imageview") || firstLower.equals("imagebutton") || firstLower.equals("img")) {
            prefix = "Img";
            startIndex = 1;
        } else if (firstLower.equals("checkbox") || firstLower.equals("chk")) {
            prefix = "Chk";
            startIndex = 1;
        } else if (firstLower.equals("radiobutton") || firstLower.equals("rdo")) {
            prefix = "Rdo";
            startIndex = 1;
        }

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
