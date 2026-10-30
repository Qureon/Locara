package com.qureon.locara.cli;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.core.parser.PageSourceParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Standalone Command Line Interface (CLI) for Locara by Qureon.
 * Allows instant reconnaissance, inspection, and .rs generation from any terminal.
 */
public class LocaraCli {

    public static void main(String[] args) {
        if (args.length == 0 || hasArg(args, "--help") || hasArg(args, "-h")) {
            printUsage();
            return;
        }

        String xmlPath = getArg(args, "--xml");
        String hint = getArg(args, "--hint");
        String outPath = getArg(args, "--out");
        String action = getArg(args, "--action", "TAP");
        boolean inspectMode = hasArg(args, "--inspect");

        if (xmlPath == null || xmlPath.isEmpty()) {
            System.err.println("❌ Error: Missing required parameter --xml <path_to_xml>");
            System.exit(1);
        }

        File xmlFile = new File(xmlPath);
        if (!xmlFile.exists()) {
            System.err.println("❌ Error: XML file not found at: " + xmlFile.getAbsolutePath());
            System.exit(1);
        }

        String xmlContent;
        try {
            xmlContent = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("❌ Error reading XML file: " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("==============================================================");
        System.out.println("  Locara by Qureon · Autonomous Self-Healing CLI Engine       ");
        System.out.println("  Adaptive Locator Intelligence · https://github.com/Qureon   ");
        System.out.println("==============================================================");

        if (inspectMode) {
            runInspectMode(xmlContent);
            return;
        }

        if (hint == null || hint.isEmpty()) {
            System.err.println("❌ Error: Missing target hint. Use --hint \"<Button Name or Description>\"");
            System.exit(1);
        }

        runHealingMode(xmlContent, hint, action, outPath);
    }

    private static void runHealingMode(String xmlContent, String hint, String action, String outPath) {
        HealConfig config = new HealConfig();
        if (outPath != null && !outPath.isEmpty()) {
            config.setKatalonProjectPath(new File(".").getAbsolutePath());
        }

        LocaraEngine engine = new LocaraEngine(config);
        HealContext.Builder builder = HealContext.builder()
                .targetHint(hint)
                .action(action);

        if (outPath != null && !outPath.isEmpty()) {
            builder.targetRsPath(outPath);
        }

        HealResult result = engine.heal(xmlContent, builder.build());

        if (result.isSuccess() && result.getCandidate() != null) {
            HealCandidate candidate = result.getCandidate();
            System.out.printf("\n🎯 MATCH FOUND! Confidence: %.1f%%\n", candidate.getConfidenceScore() * 100.0);
            System.out.println("--------------------------------------------------------------");
            System.out.println("Tag          : " + candidate.getNode().getTag());
            System.out.println("Resource ID  : " + candidate.getNode().getResourceId());
            System.out.println("Text         : " + candidate.getNode().getText());
            System.out.println("Content-Desc : " + candidate.getNode().getContentDesc());
            System.out.println("Optimal XPath: " + candidate.getSuggestedXPath());
            System.out.println("Heal Reason  : " + candidate.getHealReason());
            System.out.println("Bounds       : " + candidate.getNode().getBounds().x + "," + candidate.getNode().getBounds().y + " (" +
                    candidate.getNode().getBounds().width + "x" + candidate.getNode().getBounds().height + ")");

            if (result.getGeneratedRsFile() != null) {
                System.out.println("\n✅ Generated Katalon .rs Object Repository:");
                System.out.println("   📄 " + result.getGeneratedRsFile().getAbsolutePath());
            }
        } else {
            System.out.println("\n❌ Healing Failed: " + result.getMessage());
            System.exit(2);
        }
    }

    private static void runInspectMode(String xmlContent) {
        try {
            PageSourceParser parser = new PageSourceParser();
            List<UIElementNode> nodes = parser.parse(xmlContent);
            System.out.println("🔎 Found " + nodes.size() + " total UI nodes in hierarchy.");
            System.out.println("\nInteractive Candidates (Clickable / Inputs):");
            System.out.println("--------------------------------------------------------------");
            int count = 0;
            for (UIElementNode node : nodes) {
                if (node.isClickable() || node.hasText() || node.hasResourceId()) {
                    count++;
                    System.out.printf("[%02d] %-28s | ID: %-25s | Text: '%s'\n",
                            count,
                            truncate(node.getTag(), 28),
                            truncate(node.getStrippedResourceId(), 25),
                            truncate(node.getText(), 30));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to inspect XML: " + e.getMessage());
        }
    }

    private static String getArg(String[] args, String flag) {
        return getArg(args, flag, null);
    }

    private static String getArg(String[] args, String flag, String defaultVal) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase(flag) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return defaultVal;
    }

    private static boolean hasArg(String[] args, String flag) {
        for (String a : args) {
            if (a.equalsIgnoreCase(flag)) return true;
        }
        return false;
    }

    private static String truncate(String str, int max) {
        if (str == null) return "";
        return str.length() > max ? str.substring(0, max - 3) + "..." : str;
    }

    private static void printUsage() {
        System.out.println("Locara by Qureon CLI v1.3.0 — Autonomous Mobile Recon & Synthesizer");
        System.out.println("Usage:");
        System.out.println("  java -jar locara-1.3.0.jar --xml <path> --hint <hint> [options]");
        System.out.println("\nOptions:");
        System.out.println("  --xml <path>       Path to Appium pageSource XML dump file (Required)");
        System.out.println("  --hint <text>      Semantic description or label of the target element");
        System.out.println("  --out <path>       Target path for generated .rs file (e.g. Object Repository/Screen/btn.rs)");
        System.out.println("  --action <action>  Action type: TAP (default) or SET_TEXT");
        System.out.println("  --inspect          List all interactive UI elements discovered in the XML");
        System.out.println("  --help, -h         Show this help screen");
    }
}
