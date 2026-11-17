package com.qureon.locara.cli.audit;

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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autonomous Code Naming Intelligence & Convention Standardization Auditor (MODE A: Read-Only).
 */
public class CodeNamingAuditor {

    private static final Set<String> GENERIC_NAMES = new HashSet<>(Arrays.asList(
            "data", "temp", "val", "value", "item", "object", "obj", "result", "x", "abc", "test", "variable", "var"
    ));
    private static final Pattern GROOVY_VAR_DECL =
            Pattern.compile("(?:def|String|int|long|boolean|Map|List)\\s+([a-zA-Z0-9_]+)\\s*=");

    private final AutoVariableSynthesizer avs = new AutoVariableSynthesizer();

    public List<CodeNamingFinding> auditProject(File rootDir) {
        List<CodeNamingFinding> findings = new ArrayList<>();
        if (rootDir != null && rootDir.exists()) {
            scanDirectory(rootDir, rootDir, findings);
        }
        return findings;
    }

    public String generateMarkdownReport(List<CodeNamingFinding> findings, String projectName) {
        return CodeNamingRuleHelpers.renderMarkdownReport(findings, projectName);
    }

    private void scanDirectory(File current, File rootDir, List<CodeNamingFinding> findings) {
        String dirName = current.getName();
        if (dirName.startsWith(".") || "build".equals(dirName) || "out".equals(dirName)) return;

        File[] files = current.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectory(f, rootDir, findings);
            } else {
                String name = f.getName().toLowerCase(Locale.ROOT);
                String relPath = getRelativePath(rootDir, f);
                try {
                    if (name.endsWith(".rs")) auditRsFile(f, relPath, findings);
                    else if (name.endsWith(".tc")) auditTcFile(f, relPath, findings);
                    else if (name.endsWith(".groovy")) auditGroovyFile(f, relPath, findings);
                } catch (Exception ignored) {}
            }
        }
    }

    private void auditRsFile(File rsFile, String relPath, List<CodeNamingFinding> findings) {
        String baseName = rsFile.getName().replace(".rs", "").replace(".healed", "");
        if (baseName.matches("^\\d+\\.\\s*.*")) {
            String cleanName = baseName.replaceFirst("^\\d+\\.\\s*", "");
            findings.add(new CodeNamingFinding(
                    relPath, "Filename", baseName, "TEST_OBJECT",
                    "LEGACY_NUMBERED_PREFIX", "PASCAL_CASE_STANDARDIZED",
                    "Legacy sequential number prefix degrades reusability and locator maintainability",
                    CodeNamingRuleHelpers.suggestCanonicalRsName(cleanName),
                    "Object Repository entities should be named by responsibility and role, not execution order",
                    "MEDIUM"
            ));
        }

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

        List<String> crypticRsWords = new ArrayList<>();
        String expandedRs = CodeNamingRuleHelpers.expandCrypticTokens(
                CodeNamingRuleHelpers.splitIdentifierTokens(baseName), false, crypticRsWords);
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
        for (TestCaseVariable v : avs.parseVariables(tcFile)) {
            String vName = v.getName();
            if (GENERIC_NAMES.contains(vName.toLowerCase(Locale.ROOT))) {
                findings.add(new CodeNamingFinding(
                        relPath, "Variable: " + vName, vName, "VARIABLE",
                        "GENERIC_IDENTIFIER", "DESCRIPTIVE_DOMAIN_NAME",
                        "Meaningless generic variable name violates Section 4.1 specification",
                        CodeNamingRuleHelpers.suggestDomainVariableName(vName, relPath, ""),
                        "Variables must communicate their specific financial or behavioral role",
                        "HIGH"
                ));
            }

            List<String> crypticVarWords = new ArrayList<>();
            String expandedVar = CodeNamingRuleHelpers.expandCrypticTokens(
                    CodeNamingRuleHelpers.splitIdentifierTokens(vName), true, crypticVarWords);
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
                if (varName.equalsIgnoreCase("x") && (contextLine.contains("getX()") || contextLine.contains("Location")
                        || line.startsWith("int ") || line.startsWith("double ") || line.startsWith("float "))) {
                    continue;
                }
                if (GENERIC_NAMES.contains(varName.toLowerCase(Locale.ROOT)) && !varName.equals("result")) {
                    findings.add(new CodeNamingFinding(
                            relPath, "Line " + (i + 1), varName, "VARIABLE",
                            "GENERIC_IDENTIFIER", "DESCRIPTIVE_DOMAIN_NAME",
                            "Generic local variable '" + varName + "' obscures data semantics",
                            CodeNamingRuleHelpers.suggestDomainVariableName(varName, relPath, contextLine),
                            "Local variables should convey purpose and conceptual role",
                            "LOW"
                    ));
                }
            }
        }
    }

    private String getRelativePath(File root, File file) {
        try {
            return root.toPath().relativize(file.toPath()).toString().replace("\\", "/");
        } catch (Exception e) {
            return file.getName();
        }
    }
}
