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
    private static final Pattern BINDING_VAR_DECL_PATTERN = Pattern.compile(
            "^([ \\t]*(?:String|def)\\s+[a-zA-Z_][a-zA-Z0-9_]*\\s*=[^\\r\\n]*binding\\.[^\\r\\n]*(?:\\r?\\n|$))",
            Pattern.MULTILINE
    );

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
     * Aligns binding-backed variable declarations outside try-catch blocks in a Groovy script.
     */
    public String alignScriptContent(String scriptContent) {
        if (scriptContent == null || scriptContent.trim().isEmpty()) {
            return scriptContent;
        }

        Matcher mTry = TRY_BLOCK_PATTERN.matcher(scriptContent);
        if (!mTry.find()) {
            return scriptContent;
        }

        String updated = moveBindingDeclarationsOutsideTry(scriptContent);
        updated = moveVariableOutsideTry(updated, "passwordLogin");
        updated = moveVariableOutsideTry(updated, "nomorRekeningSumber");
        updated = moveVariableOutsideTry(updated, "teksReksa");
        updated = moveVariableOutsideTry(updated, "teksObligasi");
        return updated;
    }

    private String moveBindingDeclarationsOutsideTry(String content) {
        String current = content;
        boolean moved;
        do {
            moved = false;
            Matcher mTry = TRY_BLOCK_PATTERN.matcher(current);
            if (!mTry.find()) {
                break;
            }
            int openBrace = mTry.end() - 1;
            int closeBrace = findMatchingBrace(current, openBrace);
            if (closeBrace < 0) {
                break;
            }
            Matcher mDecl = BINDING_VAR_DECL_PATTERN.matcher(current);
            while (mDecl.find()) {
                if (mDecl.start() > openBrace && mDecl.start() < closeBrace) {
                    current = relocateDeclarationBeforeTry(current, mDecl.start(), mDecl.end(), mDecl.group(1).trim());
                    moved = true;
                    break;
                }
            }
        } while (moved);
        return current;
    }

    private String moveVariableOutsideTry(String content, String varName) {
        Matcher mTry = TRY_BLOCK_PATTERN.matcher(content);
        if (!mTry.find()) {
            return content;
        }
        int openBrace = mTry.end() - 1;
        int closeBrace = findMatchingBrace(content, openBrace);
        if (closeBrace < 0) {
            return content;
        }

        Pattern declPattern = Pattern.compile(
                "^([ \\t]*(?:String|def)\\s+" + Pattern.quote(varName) + "\\s*=[^\\r\\n]*(?:\\r?\\n|$))",
                Pattern.MULTILINE
        );
        Matcher mDecl = declPattern.matcher(content);

        while (mDecl.find()) {
            if (mDecl.start() > openBrace && mDecl.start() < closeBrace) {
                return relocateDeclarationBeforeTry(content, mDecl.start(), mDecl.end(), mDecl.group(1).trim());
            }
        }
        return content;
    }

    /**
     * Finds the closing brace matching the try block's opening '{' while skipping comments and quoted literals
     * so declarations inside catch/finally or after the try block are never moved.
     */
    private static int findMatchingBrace(String content, int openBraceIdx) {
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int len = content.length();

        for (int i = openBraceIdx; i < len; i++) {
            char c = content.charAt(i);
            char next = (i + 1 < len) ? content.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inSingle) {
                if (c == '\\') { i++; continue; }
                if (c == '\'') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inDouble = false;
                continue;
            }

            if (c == '/' && next == '/') { inLineComment = true; i++; continue; }
            if (c == '/' && next == '*') { inBlockComment = true; i++; continue; }
            if (c == '\'') { inSingle = true; continue; }
            if (c == '"') { inDouble = true; continue; }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String relocateDeclarationBeforeTry(String content, int declStart, int declEnd, String cleanDecl) {
        String withoutLine = content.substring(0, declStart) + content.substring(declEnd);
        Matcher mTryNew = TRY_BLOCK_PATTERN.matcher(withoutLine);
        if (mTryNew.find()) {
            String indent = mTryNew.group(1);
            return withoutLine.substring(0, mTryNew.start())
                    + indent + cleanDecl + "\n\n"
                    + withoutLine.substring(mTryNew.start());
        }
        return content;
    }
}
