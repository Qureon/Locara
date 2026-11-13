package com.qureon.locara.katalon.variable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autonomous Variable Synthesizer (AVS) for Katalon Studio Test Case XML Entities (.tc).
 * Reads, verifies, injects, and synchronizes the Menu Variables (Tab Variables in Katalon UI)
 * ensuring strict Quad-Trinity alignment and zero manual variable configuration overhead.
 */
public class AutoVariableSynthesizer {

    private static final Pattern VARIABLE_BLOCK_PATTERN = Pattern.compile(
            "<variable>(?:(?!</variable>)[\\s\\S])*?<name>([^<]+)</name>(?:(?!</variable>)[\\s\\S])*?</variable>",
            Pattern.DOTALL
    );

    private static final Pattern ID_PATTERN = Pattern.compile("<id>([^<]+)</id>");
    private static final Pattern DEF_VAL_PATTERN = Pattern.compile("<defaultValue>([\\s\\S]*?)</defaultValue>");
    private static final Pattern DESC_PATTERN = Pattern.compile("<description>([\\s\\S]*?)</description>");
    private static final Pattern MASKED_PATTERN = Pattern.compile("<masked>([^<]+)</masked>");

    /**
     * Parses existing variables from a .tc file.
     */
    public List<TestCaseVariable> parseVariables(File tcFile) throws IOException {
        if (tcFile == null || !tcFile.exists()) {
            return Collections.emptyList();
        }
        String content = Files.readString(tcFile.toPath(), StandardCharsets.UTF_8);
        return parseVariablesFromContent(content);
    }

    /**
     * Parses existing variables from XML content.
     */
    public List<TestCaseVariable> parseVariablesFromContent(String xmlContent) {
        List<TestCaseVariable> result = new ArrayList<>();
        if (xmlContent == null || !xmlContent.contains("<variable>")) {
            return result;
        }

        Matcher m = VARIABLE_BLOCK_PATTERN.matcher(xmlContent);
        while (m.find()) {
            String block = m.group(0);
            String name = m.group(1).trim();

            String id = extractTag(ID_PATTERN, block);
            String defVal = extractTag(DEF_VAL_PATTERN, block);
            String desc = extractTag(DESC_PATTERN, block);
            String maskedStr = extractTag(MASKED_PATTERN, block);
            boolean masked = "true".equalsIgnoreCase(maskedStr);

            result.add(new TestCaseVariable(name, defVal, desc, id, masked));
        }
        return result;
    }

    /**
     * Checks if a variable name already exists in a .tc file.
     */
    public boolean hasVariable(File tcFile, String varName) throws IOException {
        List<TestCaseVariable> vars = parseVariables(tcFile);
        for (TestCaseVariable v : vars) {
            if (v.getName().equalsIgnoreCase(varName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Injects variables into a .tc file if not already present.
     * Preserves existing XML content, encoding, and GUIDs.
     */
    public boolean synthesizeVariables(File tcFile, List<TestCaseVariable> variablesToAdd) throws IOException {
        if (tcFile == null || !tcFile.exists() || variablesToAdd == null || variablesToAdd.isEmpty()) {
            return false;
        }
        String content = Files.readString(tcFile.toPath(), StandardCharsets.UTF_8);
        String updated = synthesizeVariablesContent(content, variablesToAdd);
        if (!content.equals(updated)) {
            Files.writeString(tcFile.toPath(), updated, StandardCharsets.UTF_8);
            return true;
        }
        return false;
    }

    /**
     * Injects variables into XML content string.
     */
    public String synthesizeVariablesContent(String xmlContent, List<TestCaseVariable> variablesToAdd) {
        if (xmlContent == null || variablesToAdd == null || variablesToAdd.isEmpty()) {
            return xmlContent;
        }

        List<TestCaseVariable> existing = parseVariablesFromContent(xmlContent);
        List<TestCaseVariable> toInject = new ArrayList<>();

        for (TestCaseVariable add : variablesToAdd) {
            boolean alreadyPresent = false;
            for (TestCaseVariable ex : existing) {
                if (ex.getName().equalsIgnoreCase(add.getName())) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                toInject.add(add);
            }
        }

        if (toInject.isEmpty()) {
            return xmlContent;
        }

        StringBuilder linksXml = new StringBuilder();
        StringBuilder varsXml = new StringBuilder();

        for (TestCaseVariable v : toInject) {
            String varId = (v.getVariableId() != null && !v.getVariableId().isEmpty())
                    ? v.getVariableId()
                    : UUID.randomUUID().toString();

            linksXml.append("   <variableLinks>\n");
            linksXml.append("      <testDataLinkId></testDataLinkId>\n");
            linksXml.append("      <type>DEFAULT</type>\n");
            linksXml.append("      <value></value>\n");
            linksXml.append("      <variableId>").append(varId).append("</variableId>\n");
            linksXml.append("   </variableLinks>\n");

            varsXml.append("   <variable>\n");
            varsXml.append("      <defaultValue>").append(escapeXml(v.getDefaultValue())).append("</defaultValue>\n");
            varsXml.append("      <description>").append(escapeXml(v.getDescription())).append("</description>\n");
            varsXml.append("      <id>").append(varId).append("</id>\n");
            varsXml.append("      <masked>").append(v.isMasked()).append("</masked>\n");
            varsXml.append("      <name>").append(escapeXml(v.getName())).append("</name>\n");
            varsXml.append("   </variable>\n");
        }

        int endTagPos = xmlContent.lastIndexOf("</TestCaseEntity>");
        if (endTagPos == -1) {
            return xmlContent;
        }

        return xmlContent.substring(0, endTagPos)
                + linksXml.toString()
                + varsXml.toString()
                + "</TestCaseEntity>";
    }

    /**
     * Updates an existing variable's defaultValue or synthesizes it if missing.
     */
    public boolean syncVariableDefault(File tcFile, String varName, String newDefaultValue) throws IOException {
        if (tcFile == null || !tcFile.exists() || varName == null) {
            return false;
        }
        String content = Files.readString(tcFile.toPath(), StandardCharsets.UTF_8);
        String updated = syncVariableDefaultContent(content, varName, newDefaultValue, "Auto-synced by Locara");
        if (!content.equals(updated)) {
            Files.writeString(tcFile.toPath(), updated, StandardCharsets.UTF_8);
            return true;
        }
        return false;
    }

    public String syncVariableDefaultContent(String xmlContent, String varName, String newDefaultValue, String description) {
        if (xmlContent == null || varName == null) {
            return xmlContent;
        }
        List<TestCaseVariable> existing = parseVariablesFromContent(xmlContent);
        boolean found = false;
        for (TestCaseVariable ex : existing) {
            if (ex.getName().equalsIgnoreCase(varName)) {
                found = true;
                break;
            }
        }

        if (found) {
            // Regex replace defaultValue within matching variable block
            Pattern specificBlock = Pattern.compile(
                    "(<variable>(?:(?!</variable>)[\\s\\S])*?<name>" + Pattern.quote(varName) + "</name>(?:(?!</variable>)[\\s\\S])*?</variable>)",
                    Pattern.DOTALL
            );
            Matcher m = specificBlock.matcher(xmlContent);
            if (m.find()) {
                String oldBlock = m.group(1);
                String newBlock = oldBlock.replaceAll(
                        "<defaultValue>[\\s\\S]*?</defaultValue>",
                        "<defaultValue>" + Matcher.quoteReplacement(escapeXml(newDefaultValue)) + "</defaultValue>"
                );
                return xmlContent.replace(oldBlock, newBlock);
            }
            return xmlContent;
        } else {
            // Inject as new variable
            List<TestCaseVariable> toAdd = Collections.singletonList(
                    new TestCaseVariable(varName, newDefaultValue, description)
            );
            return synthesizeVariablesContent(xmlContent, toAdd);
        }
    }

    private String extractTag(Pattern pattern, String block) {
        Matcher m = pattern.matcher(block);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static final Set<String> BLACKLISTED_GENERIC_VAR_NAMES = new HashSet<>(Arrays.asList(
            "data", "temp", "val", "value", "item", "object", "obj", "result", "x", "abc", "test", "variable", "var"
    ));

    /**
     * Evaluates variable naming against Section 4.1 Code Naming Intelligence standards.
     * Returns true if variable communicates domain purpose and is not a blacklisted generic identifier.
     */
    public static boolean isValidDomainVariableName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String clean = name.trim();
        if (clean.length() < 2) {
            return false;
        }
        if (BLACKLISTED_GENERIC_VAR_NAMES.contains(clean.toLowerCase())) {
            return false;
        }
        // Must follow camelCase: starts with lowercase letter, alphanumeric
        return clean.matches("^[a-z][a-zA-Z0-9_]*$");
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
