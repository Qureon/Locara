package com.qureon.locara.cli;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Standalone Command Line Interface (CLI) router for Locara by Qureon.
 */
public class LocaraCli {

    public static void main(String[] args) {
        int exitCode = execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int execute(String[] args) {
        if (args == null || args.length == 0 || hasArg(args, "--help") || hasArg(args, "-h")) {
            printUsage();
            return 0;
        }

        String command = args[0];
        if ("sanitize-or".equalsIgnoreCase(command) || hasArg(args, "--sanitize-or")) {
            String sanitizePath = getArg(args, "--path", null);
            if (sanitizePath == null) sanitizePath = getArg(args, "--dir", "Object Repository");
            return CliCommandHandlers.runSanitizeMode(
                    sanitizePath, hasArg(args, "--fix"), !hasArg(args, "--no-backup"));
        }
        if ("inspect-fields".equalsIgnoreCase(command) || hasArg(args, "--inspect-fields")) {
            String xmlPath = resolveFlagOrPositional(args, "--xml");
            if (xmlPath == null) {
                System.err.println("❌ Error: Missing --xml <path_to_xml>");
                return 1;
            }
            return CliCommandHandlers.runInspectFieldsMode(xmlPath);
        }
        if ("extract-account".equalsIgnoreCase(command) || hasArg(args, "--extract-account")) {
            String xmlPath = resolveFlagOrPositional(args, "--xml");
            if (xmlPath == null) {
                System.err.println("❌ Error: Missing --xml <path_to_xml>");
                return 1;
            }
            return CliCommandHandlers.runExtractAccountMode(xmlPath);
        }
        if ("sync-tc".equalsIgnoreCase(command) || hasArg(args, "--sync-tc")) {
            return CliCommandHandlers.runSyncTcMode(
                    resolveFlagOrPositional(args, "--tc"), getArg(args, "--var", null),
                    getArg(args, "--default", "''"), getArg(args, "--desc", "Auto-synced by Locara"));
        }
        if ("align-script".equalsIgnoreCase(command) || hasArg(args, "--align-script")) {
            return CliCommandHandlers.runAlignScriptMode(resolveFlagOrPositional(args, "--script"));
        }
        if ("audit-naming".equalsIgnoreCase(command) || hasArg(args, "--audit-naming")) {
            String targetPath = getArg(args, "--path", null);
            if (targetPath == null) targetPath = getArg(args, "--dir", null);
            if (targetPath == null && args.length > 1 && !args[1].startsWith("-")) targetPath = args[1];
            return CliCommandHandlers.runAuditNamingMode((targetPath == null || targetPath.isEmpty()) ? "." : targetPath);
        }

        return handleXmlOrHealCommand(args);
    }

    private static int handleXmlOrHealCommand(String[] args) {
        String xmlPath = getArg(args, "--xml", null);
        if (xmlPath == null || xmlPath.isEmpty()) {
            System.err.println("❌ Error: Missing required parameter --xml <path_to_xml>");
            return 1;
        }
        File xmlFile = new File(xmlPath);
        if (!xmlFile.exists()) {
            System.err.println("❌ Error: XML file not found at: " + xmlFile.getAbsolutePath());
            return 1;
        }
        String xmlContent;
        try {
            xmlContent = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("❌ Error reading XML file: " + e.getMessage());
            return 1;
        }

        if (hasArg(args, "--inspect")) {
            return CliCommandHandlers.runInspectMode(xmlContent);
        }
        String hint = getArg(args, "--hint", null);
        if (hint == null || hint.isEmpty()) {
            System.err.println("❌ Error: Missing target hint. Use --hint \"<Button Name or Description>\"");
            return 1;
        }
        return CliCommandHandlers.runHealingMode(
                xmlContent,
                hint,
                getArg(args, "--action", "TAP"),
                getArg(args, "--out", null),
                getArg(args, "--project-dir", null),
                !hasArg(args, "--no-audit")
        );
    }

    private static String resolveFlagOrPositional(String[] args, String flag) {
        String val = getArg(args, flag, null);
        if (val == null && args.length > 1 && !args[1].startsWith("-")) {
            return args[1];
        }
        return val;
    }

    private static String getArg(String[] args, String flag, String defaultVal) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase(flag) && i + 1 < args.length) return args[i + 1];
        }
        return defaultVal;
    }

    private static boolean hasArg(String[] args, String flag) {
        for (String a : args) {
            if (a.equalsIgnoreCase(flag)) return true;
        }
        return false;
    }

    private static void printUsage() {
        System.out.println("Locara by Qureon CLI v1.16.1 — Autonomous Mobile Recon & Synthesizer");
        System.out.println("Usage:");
        System.out.println("  locara --xml <path> --hint <hint> [--action TAP] [--out <rs>] [--project-dir <dir>] [--no-audit]");
        System.out.println("  locara sanitize-or --path <path> [--fix] [--no-backup]");
        System.out.println("  locara inspect-fields --xml <path>");
        System.out.println("  locara extract-account --xml <path>");
        System.out.println("  locara sync-tc --tc <path.tc> --var <varName> [--default <val>] [--desc <desc>]");
        System.out.println("  locara align-script --script <path.groovy>");
        System.out.println("  locara audit-naming [--path <dir>]");
    }
}
