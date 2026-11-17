package com.qureon.locara.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Locara CLI Subcommand Routing & Exit Code Suite")
class LocaraCliTest {

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <hierarchy rotation="0">
              <android.widget.FrameLayout bounds="[0,0][1080,2400]">
                <android.widget.TextView resource-id="com.example.app:id/tv_nominal_lbl" text="Nominal" bounds="[40,100][240,150]"/>
                <android.widget.TextView resource-id="com.example.app:id/tv_nominal_val" text="Rp 250.000" bounds="[300,100][680,150]"/>
                <android.widget.TextView resource-id="com.example.app:id/tv_source_acc" text="Tabungan Utama 900012345678 - RIO" bounds="[40,220][800,280]"/>
                <android.widget.Button resource-id="com.example.app:id/btn_lanjut" text="Lanjut" bounds="[540,1920][1020,2060]" clickable="true"/>
              </android.widget.FrameLayout>
            </hierarchy>
            """;

    private record CliRun(int exitCode, String stdout, String stderr) {}

    private CliRun invokeCli(String... args) {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
            int code = LocaraCli.execute(args);
            return new CliRun(
                    code,
                    outBuf.toString(StandardCharsets.UTF_8),
                    errBuf.toString(StandardCharsets.UTF_8)
            );
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
    }

    @Test
    void helpAndEmptyArgsReturnZeroWithVersionBanner() {
        CliRun empty = invokeCli();
        assertEquals(0, empty.exitCode());
        assertTrue(empty.stdout().contains("Locara by Qureon CLI"));
        assertTrue(empty.stdout().contains("audit-naming"));

        CliRun help = invokeCli("--help");
        assertEquals(0, help.exitCode());
        assertTrue(help.stdout().contains("--xml"));
    }

    @Test
    void missingAndNonexistentXmlReturnExitCodeOne() {
        CliRun missing = invokeCli("--hint", "Lanjut");
        assertEquals(1, missing.exitCode());
        assertTrue(missing.stderr().contains("--xml"));

        CliRun missingFile = invokeCli("--xml", "/nonexistent/locara-missing-999.xml", "--hint", "Lanjut");
        assertEquals(1, missingFile.exitCode());
        assertTrue(missingFile.stderr().contains("not found"));

        CliRun missingInspectFieldsXml = invokeCli("inspect-fields");
        assertEquals(1, missingInspectFieldsXml.exitCode());
        assertTrue(missingInspectFieldsXml.stderr().contains("--xml"));
    }

    @Test
    void healModeReturnsZeroOnMatchAndTwoOnMiss(@TempDir Path tempDir) throws Exception {
        Path xmlFile = tempDir.resolve("screen.xml");
        Files.writeString(xmlFile, SAMPLE_XML, StandardCharsets.UTF_8);

        CliRun matched = invokeCli("--xml", xmlFile.toString(), "--hint", "Lanjut", "--action", "TAP");
        assertEquals(0, matched.exitCode());
        assertTrue(matched.stdout().contains("MATCH FOUND"));
        assertTrue(matched.stdout().contains("btn_lanjut"));

        CliRun missed = invokeCli("--xml", xmlFile.toString(), "--hint", "NonExistentQuantumButtonXYZ");
        assertEquals(2, missed.exitCode());
        assertTrue(missed.stdout().contains("Healing Failed"));
    }

    @Test
    void inspectAndInspectFieldsAndExtractAccountSucceed(@TempDir Path tempDir) throws Exception {
        Path xmlFile = tempDir.resolve("screen.xml");
        Files.writeString(xmlFile, SAMPLE_XML, StandardCharsets.UTF_8);

        CliRun inspect = invokeCli("--xml", xmlFile.toString(), "--inspect");
        assertEquals(0, inspect.exitCode());
        assertTrue(inspect.stdout().contains("total UI nodes"));

        CliRun fields = invokeCli("inspect-fields", "--xml", xmlFile.toString());
        assertEquals(0, fields.exitCode());
        assertTrue(fields.stdout().contains("Nominal"));

        CliRun account = invokeCli("extract-account", "--xml", xmlFile.toString());
        assertEquals(0, account.exitCode());
        assertTrue(account.stdout().contains("Contextual Entity"));
    }

    @Test
    void sanitizeAndAuditNamingHandleWorkspaceDirectories(@TempDir Path tempDir) throws Exception {
        Path missing = tempDir.resolve("does-not-exist");
        assertEquals(1, invokeCli("sanitize-or", "--path", missing.toString()).exitCode());
        assertEquals(1, invokeCli("audit-naming", "--path", missing.toString()).exitCode());

        CliRun sanitizeOk = invokeCli("sanitize-or", "--path", tempDir.toString());
        assertEquals(0, sanitizeOk.exitCode());
        assertTrue(sanitizeOk.stdout().contains("Sanitization Summary"));

        Files.writeString(tempDir.resolve("Sample.groovy"), "class Sample {}\n", StandardCharsets.UTF_8);
        CliRun auditOk = invokeCli("audit-naming", "--path", tempDir.toString());
        assertEquals(0, auditOk.exitCode());
        assertTrue(auditOk.stdout().contains("LOCARA CODE NAMING AUDITOR"));
    }
}
