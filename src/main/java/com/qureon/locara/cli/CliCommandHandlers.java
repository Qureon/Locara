package com.qureon.locara.cli;

import com.qureon.locara.cli.audit.CodeNamingAuditor;
import com.qureon.locara.cli.audit.CodeNamingFinding;
import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.context.ContextualEntityResolver;
import com.qureon.locara.core.context.ResolvedAccount;
import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.core.parser.FieldRelationshipParser;
import com.qureon.locara.core.parser.PageSourceParser;
import com.qureon.locara.core.sanitizer.LocaraOrSanitizer;
import com.qureon.locara.katalon.variable.AutoVariableSynthesizer;
import com.qureon.locara.katalon.variable.ScriptVariableAligner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * Modular subcommand handlers for {@link LocaraCli}, returning structured exit codes
 * without calling {@link System#exit(int)} directly for clean unit testability.
 */
final class CliCommandHandlers {

    private CliCommandHandlers() {}

    static int runSanitizeMode(String path, boolean fix, boolean backup) {
        File target = new File(path);
        if (!target.exists()) {
            System.err.println("❌ Error: Target path not found: " + target.getAbsolutePath());
            return 1;
        }
        System.out.println("==============================================================");
        System.out.println("  Locara by Qureon · Object Repository Hygiene & Sanitizer     ");
        System.out.println("  Target Path: " + target.getAbsolutePath());
        System.out.println("  Mode       : " + (fix ? "FIX (Auto-rewrite brittle XPaths)" : "DRY-RUN (Scan only)"));
        System.out.println("  Backup     : " + (backup ? "ENABLED (.bak)" : "DISABLED"));
        System.out.println("==============================================================");
        try {
            LocaraOrSanitizer.SanitizeSummary summary = new LocaraOrSanitizer().scanAndSanitize(target, fix, backup);
            System.out.println("\n📊 Sanitization Summary:");
            System.out.println("   • Total .rs files scanned: " + summary.getTotalScanned());
            System.out.println("   • Brittle hierarchy XPaths: " + summary.getBrittleFound());
            System.out.println("   • Files successfully sanitized: " + summary.getTotalSanitized());
            if (!fix && summary.getBrittleFound() > 0) {
                System.out.println("\n💡 Tip: Run with --fix to automatically replace brittle hierarchy XPaths with resilient relative XPaths.");
            }
            return 0;
        } catch (Exception e) {
            System.err.println("❌ Sanitization error: " + e.getMessage());
            return 1;
        }
    }

    static int runHealingMode(String xmlContent, String hint, String action, String outPath) {
        HealConfig config = new HealConfig();
        if (outPath != null && !outPath.isEmpty()) {
            config.setKatalonProjectPath(new File(".").getAbsolutePath());
            config.setAutoGenerateRs(true);
        }
        HealContext.Builder builder = HealContext.builder().targetHint(hint).action(action);
        if (outPath != null && !outPath.isEmpty()) builder.targetRsPath(outPath);

        HealResult result = new LocaraEngine(config).heal(xmlContent, builder.build());
        if (result.isSuccess() && result.getCandidate() != null) {
            HealCandidate c = result.getCandidate();
            System.out.printf(Locale.US, "\n🎯 MATCH FOUND! Confidence: %.1f%%\n", c.getConfidenceScore() * 100.0);
            System.out.println("--------------------------------------------------------------");
            System.out.println("Tag          : " + c.getNode().getTag());
            System.out.println("Resource ID  : " + c.getNode().getResourceId());
            System.out.println("Text         : " + c.getNode().getText());
            System.out.println("Content-Desc : " + c.getNode().getContentDesc());
            System.out.println("Optimal XPath: " + c.getSuggestedXPath());
            System.out.println("Heal Reason  : " + c.getHealReason());
            if (result.getGeneratedRsFile() != null) {
                System.out.println("\n✅ Generated Katalon .rs Object Repository:\n   📄 " + result.getGeneratedRsFile().getAbsolutePath());
            }
            return 0;
        }
        System.out.println("\n❌ Healing Failed: " + result.getMessage());
        return 2;
    }

    static int runInspectMode(String xmlContent) {
        try {
            List<UIElementNode> nodes = new PageSourceParser().parse(xmlContent);
            System.out.println("🔎 Found " + nodes.size() + " total UI nodes in hierarchy.");
            System.out.println("\nInteractive Candidates (Clickable / Inputs):");
            System.out.println("--------------------------------------------------------------");
            int count = 0;
            for (UIElementNode node : nodes) {
                if (node.isClickable() || node.hasText() || node.hasResourceId()) {
                    count++;
                    System.out.printf("[%02d] %-28s | ID: %-25s | Text: '%s'\n",
                            count, truncate(node.getTag(), 28),
                            truncate(node.getStrippedResourceId(), 25), truncate(node.getText(), 30));
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("❌ Failed to inspect XML: " + e.getMessage());
            return 1;
        }
    }

    static int runInspectFieldsMode(String xmlPath) {
        try {
            String xmlContent = Files.readString(new File(xmlPath).toPath(), StandardCharsets.UTF_8);
            List<UIElementNode> nodes = new PageSourceParser().parse(xmlContent);
            FieldRelationshipParser.ParsedDocument doc = new FieldRelationshipParser().parse(nodes);
            System.out.println("==============================================================");
            System.out.println("  Locara by Qureon · Universal Form & Field Relationship Table ");
            System.out.println("==============================================================");
            System.out.println(doc.toSummaryTable());
            if (doc.getTotalEntry().isPresent()) {
                System.out.println("🎯 Terminal Total Detected: " + doc.getTotalEntry().get().getRawLabel()
                        + " -> " + doc.getTotalEntry().get().getValue());
            }
            return 0;
        } catch (Exception e) {
            System.err.println("❌ Failed to parse field relationships: " + e.getMessage());
            return 1;
        }
    }

    static int runExtractAccountMode(String xmlPath) {
        try {
            String xmlContent = Files.readString(new File(xmlPath).toPath(), StandardCharsets.UTF_8);
            ResolvedAccount account = new ContextualEntityResolver().resolveActiveAccount(xmlContent);
            System.out.println("==============================================================");
            System.out.println("  Locara by Qureon · Contextual Entity & Account Card Resolver");
            System.out.println("==============================================================");
            if (account != null && account.isValid()) {
                System.out.println("✅ Active Account Discovered:");
                System.out.println("💳 Account Number : " + account.getAccountNumber());
                System.out.println("🏦 Account Type   : " + account.getAccountType());
                System.out.println("👤 Account Holder : " + (account.getHolderName().isEmpty() ? "-" : account.getHolderName()));
                System.out.println("🔒 Masked Balance : " + (account.isMaskedBalancePresent() ? "YES" : "NO"));
            } else {
                System.out.println("⚠️ No active account card matching 10-16 digits discovered in XML.");
            }
            return 0;
        } catch (Exception e) {
            System.err.println("❌ Failed to extract account entity: " + e.getMessage());
            return 1;
        }
    }

    static int runSyncTcMode(String tcPath, String varName, String defVal, String desc) {
        if (tcPath == null || varName == null) {
            System.err.println("❌ Error: Missing --tc <path.tc> or --var <varName>");
            return 1;
        }
        File tcFile = new File(tcPath);
        if (!tcFile.exists()) {
            System.err.println("❌ Error: .tc file not found: " + tcFile.getAbsolutePath());
            return 1;
        }
        try {
            boolean changed = new AutoVariableSynthesizer().syncVariableDefault(tcFile, varName, defVal, desc);
            System.out.println(changed
                    ? "✅ Synchronized variable '" + varName + "' = " + defVal + " in " + tcFile.getName()
                    : "ℹ️ Variable '" + varName + "' already up to date in " + tcFile.getName());
            return 0;
        } catch (Exception e) {
            System.err.println("❌ Failed to sync .tc variable: " + e.getMessage());
            return 1;
        }
    }

    static int runAlignScriptMode(String scriptPath) {
        if (scriptPath == null) {
            System.err.println("❌ Error: Missing --script <path.groovy>");
            return 1;
        }
        File scriptFile = new File(scriptPath);
        if (!scriptFile.exists()) {
            System.err.println("❌ Error: Script file not found: " + scriptFile.getAbsolutePath());
            return 1;
        }
        try {
            boolean changed = new ScriptVariableAligner().alignScript(scriptFile);
            System.out.println(changed
                    ? "✅ Successfully aligned variables outside try block in " + scriptFile.getName()
                    : "ℹ️ Script is already compliant with outside-try scoping invariant.");
            return 0;
        } catch (Exception e) {
            System.err.println("❌ Failed to align script: " + e.getMessage());
            return 1;
        }
    }

    static int runAuditNamingMode(String targetPath) {
        File dir = new File(targetPath);
        if (!dir.exists()) {
            System.err.println("❌ Error: Target path does not exist: " + dir.getAbsolutePath());
            return 1;
        }
        CodeNamingAuditor auditor = new CodeNamingAuditor();
        List<CodeNamingFinding> findings = auditor.auditProject(dir);
        System.out.println("🔍 LOCARA CODE NAMING AUDITOR (MODE A) — Target: " + dir.getAbsolutePath());
        System.out.println(auditor.generateMarkdownReport(findings, dir.getName()));
        System.out.println("📊 Audit completed with " + findings.size() + " findings.");
        return 0;
    }

    private static String truncate(String str, int max) {
        if (str == null) return "";
        return str.length() > max ? str.substring(0, max - 3) + "..." : str;
    }
}
