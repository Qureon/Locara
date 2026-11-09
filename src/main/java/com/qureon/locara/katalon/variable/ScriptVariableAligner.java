package com.qureon.locara.katalon.variable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Script Variable Aligner for Katalon Studio Groovy Test Scripts.
 * Enforces the Strict Variable Scoping Invariant: input parameters, credentials,
 * and test data variables MUST be declared OUTSIDE/BEFORE the try-catch block
 * to preserve visibility in catch and finally blocks.
 */
public class ScriptVariableAligner {

    private static final Pattern TRY_BLOCK_PATTERN = Pattern.compile("^([ \\t]*)try\\s*\\{", Pattern.MULTILINE);

    /**
     * Aligns variable declarations in a Groovy script file.
     */
    public boolean alignScript(File scriptFile) throws IOException {
        if (scriptFile == null || !scriptFile.exists()) {
            return false;
        }
        String content = Files.readString(scriptFile.toPath(), StandardCharsets.UTF_8);
        String updated = alignScriptContent(content);
        if (!content.equals(updated)) {
            Files.writeString(scriptFile.toPath(), updated, StandardCharsets.UTF_8);
            return true;
        }
        return false;
    }

    /**
     * Aligns variable declarations in a Groovy script content string.
     */
    public String alignScriptContent(String scriptContent) {
        if (scriptContent == null || scriptContent.trim().isEmpty()) {
            return scriptContent;
        }

        String updated = scriptContent;

        // 1. Move variable declarations from inside try to outside try
        updated = moveVariableOutsideTry(updated, "passwordLogin");
        updated = moveVariableOutsideTry(updated, "nomorRekeningSumber");
        updated = moveVariableOutsideTry(updated, "teksReksa");
        updated = moveVariableOutsideTry(updated, "teksObligasi");

        // 2. Eliminate hardcoded Fakhri account references in favour of dynamic variable
        if (updated.contains("GlobalVariable.G_Rek_Mega_Fakhri") || updated.contains("GlobalVariable.G_rekMegaFakhri")) {
            updated = updated.replace("GlobalVariable.G_Rek_Mega_Fakhri", "nomorRekeningSumber");
            updated = updated.replace("GlobalVariable.G_rekMegaFakhri", "nomorRekeningSumber");

            // Ensure nomorRekeningSumber is declared outside try if not yet declared
            if (!updated.contains("String nomorRekeningSumber")) {
                updated = injectVariableOutsideTry(
                        updated,
                        "String nomorRekeningSumber = binding.hasVariable(\"nomorRekeningSumber\") && binding.getVariable(\"nomorRekeningSumber\") ? binding.getVariable(\"nomorRekeningSumber\") : \"\""
                );
            }
        }

        return updated;
    }

    private String moveVariableOutsideTry(String content, String varName) {
        Matcher mTry = TRY_BLOCK_PATTERN.matcher(content);
        if (!mTry.find()) {
            return content;
        }
        int tryPos = mTry.start();

        // Regex to find variable declaration with binding
        Pattern declPattern = Pattern.compile("([ \\t]*String\\s+" + Pattern.quote(varName) + "\\s*=[^\\n]*\\n)");
        Matcher mDecl = declPattern.matcher(content);

        while (mDecl.find()) {
            if (mDecl.start() > tryPos) {
                // It is inside try! Move it outside try.
                String fullLine = mDecl.group(1);
                String cleanDecl = fullLine.trim();

                // Remove from inside try
                String withoutLine = content.substring(0, mDecl.start()) + content.substring(mDecl.end());

                // Find try in new content
                Matcher mTryNew = TRY_BLOCK_PATTERN.matcher(withoutLine);
                if (mTryNew.find()) {
                    String indent = mTryNew.group(1);
                    return withoutLine.substring(0, mTryNew.start())
                            + indent + cleanDecl + "\n\n"
                            + withoutLine.substring(mTryNew.start());
                }
            }
        }
        return content;
    }

    private String injectVariableOutsideTry(String content, String variableDeclaration) {
        Matcher mTry = TRY_BLOCK_PATTERN.matcher(content);
        if (mTry.find()) {
            String indent = mTry.group(1);
            return content.substring(0, mTry.start())
                    + indent + variableDeclaration + "\n\n"
                    + content.substring(mTry.start());
        }
        return variableDeclaration + "\n\n" + content;
    }
}
