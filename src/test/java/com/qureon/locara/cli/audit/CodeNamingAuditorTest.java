package com.qureon.locara.cli.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CodeNamingAuditorTest {

    private final CodeNamingAuditor auditor = new CodeNamingAuditor();

    @Test
    @DisplayName("Should detect legacy sequential numbering and generic identifiers in Object Repository")
    void testAuditLegacyNumberedRsFile(@TempDir Path tempDir) throws Exception {
        Path orDir = tempDir.resolve("Object Repository/Dashboard");
        Files.createDirectories(orDir);

        Path legacyRs = orDir.resolve("01. Button - Submit.rs");
        Files.writeString(legacyRs, "<MobileElementEntity><name>01. Button - Submit</name></MobileElementEntity>", StandardCharsets.UTF_8);

        List<CodeNamingFinding> findings = auditor.auditProject(tempDir.toFile());

        assertFalse(findings.isEmpty());
        CodeNamingFinding legacyFinding = findings.stream()
                .filter(f -> f.getCurrentConvention().equals("LEGACY_NUMBERED_PREFIX"))
                .findFirst()
                .orElse(null);
        assertNotNull(legacyFinding, "Should flag 01. Button - Submit as legacy numbered prefix");
        assertEquals("BtnSubmit", legacyFinding.getProposedName());

        String report = auditor.generateMarkdownReport(findings, "TestProject");
        assertNotNull(report);
        assertTrue(report.contains("| File | Location | Identifier | Category |"));
        assertTrue(report.contains("01. Button - Submit"));
    }

    @Test
    @DisplayName("Should expand cryptic banking abbreviations while guarding English 'no' and coordinate 'x'")
    void testAbbreviationExpansionAndFalsePositiveGuards(@TempDir Path tempDir) throws Exception {
        Path orDir = tempDir.resolve("Object Repository/Transfer");
        Files.createDirectories(orDir);
        Files.writeString(orDir.resolve("TxtNoRek.rs"), "<MobileElementEntity><name>TxtNoRek</name></MobileElementEntity>", StandardCharsets.UTF_8);
        Files.writeString(orDir.resolve("TxtNoData.rs"), "<MobileElementEntity><name>TxtNoData</name></MobileElementEntity>", StandardCharsets.UTF_8);

        Path tcDir = tempDir.resolve("Test Cases/Transfer");
        Files.createDirectories(tcDir);
        String tcXml = """
                <TestCaseEntity>
                   <name>TC_Transfer</name>
                   <variable>
                      <defaultValue>''</defaultValue>
                      <description></description>
                      <id>124</id>
                      <masked>false</masked>
                      <name>noRek</name>
                   </variable>
                </TestCaseEntity>
                """;
        Files.writeString(tcDir.resolve("TC_Transfer.tc"), tcXml, StandardCharsets.UTF_8);

        Path kwDir = tempDir.resolve("Keywords/utils");
        Files.createDirectories(kwDir);
        String groovyCode = """
                int x = element.getLocation().getX()
                int y = element.getLocation().getY()
                """;
        Files.writeString(kwDir.resolve("SwipeHelper.groovy"), groovyCode, StandardCharsets.UTF_8);

        List<CodeNamingFinding> findings = auditor.auditProject(tempDir.toFile());

        assertTrue(findings.stream().anyMatch(f -> f.getIdentifier().equals("TxtNoRek") && f.getProposedName().equals("TxtNomorRekening")));
        assertTrue(findings.stream().anyMatch(f -> f.getIdentifier().equals("noRek") && f.getProposedName().equals("nomorRekening")));
        assertFalse(findings.stream().anyMatch(f -> f.getIdentifier().equals("TxtNoData")), "Should NOT flag English TxtNoData");
        assertFalse(findings.stream().anyMatch(f -> f.getIdentifier().equals("x")), "Should NOT flag spatial coordinate x");
    }

    @Test
    @DisplayName("Should detect generic variable names in .tc files")
    void testAuditGenericVariablesInTc(@TempDir Path tempDir) throws Exception {
        Path tcDir = tempDir.resolve("Test Cases/Top Up");
        Files.createDirectories(tcDir);

        String tcXml = """
                <TestCaseEntity>
                   <name>TC_TopUp_Pulsa</name>
                   <variable>
                      <defaultValue>''</defaultValue>
                      <description></description>
                      <id>123</id>
                      <masked>false</masked>
                      <name>data</name>
                   </variable>
                </TestCaseEntity>
                """;
        Files.writeString(tcDir.resolve("TC_TopUp_Pulsa.tc"), tcXml, StandardCharsets.UTF_8);

        List<CodeNamingFinding> findings = auditor.auditProject(tempDir.toFile());

        boolean foundGeneric = findings.stream().anyMatch(f -> f.getIdentifier().equals("data"));
        assertTrue(foundGeneric, "Should flag generic variable 'data'");
    }
}
