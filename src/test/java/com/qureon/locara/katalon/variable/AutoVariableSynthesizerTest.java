package com.qureon.locara.katalon.variable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AutoVariableSynthesizerTest {

    private final AutoVariableSynthesizer synthesizer = new AutoVariableSynthesizer();
    private final ScriptVariableAligner aligner = new ScriptVariableAligner();

    @Test
    @DisplayName("Should synthesize Menu Variables into empty .tc XML entity with valid UUIDs")
    public void testSynthesizeVariablesIntoEmptyTc() {
        String bareTc = """
                <?xml version="1.0" encoding="UTF-8"?>
                <TestCaseEntity>
                   <description>Test Case Otomasi</description>
                   <name>TC-P65_INVEST_REKSAD</name>
                   <tag>Investasi</tag>
                   <comment></comment>
                   <recordOption>OTHER</recordOption>
                   <testCaseGuid>5b71a544-d672-4652-aac0-7fa3a54e2f05</testCaseGuid>
                </TestCaseEntity>
                """;

        List<TestCaseVariable> toAdd = new ArrayList<>();
        toAdd.add(new TestCaseVariable("passwordLogin", "'Bankmega1'", "Password login"));
        toAdd.add(new TestCaseVariable("nomorRekeningSumber", "''", "Nomor rekening"));
        toAdd.add(new TestCaseVariable("teksReksa", "'cpf'", "Keyword reksadana"));

        String synthesized = synthesizer.synthesizeVariablesContent(bareTc, toAdd);

        assertNotNull(synthesized);
        assertTrue(synthesized.contains("<name>passwordLogin</name>"));
        assertTrue(synthesized.contains("<name>nomorRekeningSumber</name>"));
        assertTrue(synthesized.contains("<name>teksReksa</name>"));
        assertTrue(synthesized.contains("<variableLinks>"));
        assertTrue(synthesized.endsWith("</TestCaseEntity>\n") || synthesized.endsWith("</TestCaseEntity>"));

        List<TestCaseVariable> parsed = synthesizer.parseVariablesFromContent(synthesized);
        assertEquals(3, parsed.size());
        assertEquals("passwordLogin", parsed.get(0).getName());
        assertEquals("'Bankmega1'", parsed.get(0).getDefaultValue());
    }

    @Test
    @DisplayName("Should update existing variable default value without duplicating blocks")
    public void testUpdateExistingVariableDefault() {
        String existingTc = """
                <?xml version="1.0" encoding="UTF-8"?>
                <TestCaseEntity>
                   <name>TC-01</name>
                   <testCaseGuid>guid-123</testCaseGuid>
                   <variableLinks>
                      <variableId>uuid-1</variableId>
                   </variableLinks>
                   <variable>
                      <defaultValue>''</defaultValue>
                      <description>Account</description>
                      <id>uuid-1</id>
                      <masked>false</masked>
                      <name>nomorRekeningSumber</name>
                   </variable>
                </TestCaseEntity>
                """;

        String updated = synthesizer.syncVariableDefaultContent(
                existingTc,
                "nomorRekeningSumber",
                "'900040021025853'",
                "Updated by Locara"
        );

        assertTrue(updated.contains("<defaultValue>'900040021025853'</defaultValue>"));
        List<TestCaseVariable> vars = synthesizer.parseVariablesFromContent(updated);
        assertEquals(1, vars.size(), "Should not duplicate variable block");
        assertEquals("'900040021025853'", vars.get(0).getDefaultValue());
    }

    @Test
    @DisplayName("ScriptVariableAligner should move variable declarations from inside try to outside try")
    public void testAlignScriptVariablesOutsideTry() {
        String scriptInsideTry = """
                println('Start test')
                try {
                    String passwordLogin = binding.hasVariable("passwordLogin") ? binding.getVariable("passwordLogin") : "DemoPass1"
                    Mobile.callTestCase(findTestCase("Login"), [:])
                } catch (Exception e) {
                    throw e
                }
                """;

        String aligned = aligner.alignScriptContent(scriptInsideTry);

        int tryPos = aligned.indexOf("try {");
        int varPos = aligned.indexOf("String passwordLogin");

        assertTrue(varPos != -1 && tryPos != -1);
        assertTrue(varPos < tryPos, "Variable declaration MUST be placed before/outside try block");
    }

    @Test
    @DisplayName("Should validate variable naming against Code Naming Intelligence Section 4.1 standards")
    public void testValidateVariableNaming() {
        // Valid domain variables
        assertTrue(AutoVariableSynthesizer.isValidDomainVariableName("sourceAccountNumber"));
        assertTrue(AutoVariableSynthesizer.isValidDomainVariableName("targetAccountNumber"));
        assertTrue(AutoVariableSynthesizer.isValidDomainVariableName("transferAmount"));
        assertTrue(AutoVariableSynthesizer.isValidDomainVariableName("mpinInput"));

        // Blacklisted generic names
        assertFalse(AutoVariableSynthesizer.isValidDomainVariableName("data"));
        assertFalse(AutoVariableSynthesizer.isValidDomainVariableName("temp"));
        assertFalse(AutoVariableSynthesizer.isValidDomainVariableName("val"));
        assertFalse(AutoVariableSynthesizer.isValidDomainVariableName("value"));
        assertFalse(AutoVariableSynthesizer.isValidDomainVariableName("item"));
        assertFalse(AutoVariableSynthesizer.isValidDomainVariableName("result"));
        assertFalse(AutoVariableSynthesizer.isValidDomainVariableName("x"));

        // Invalid casing or empty
        assertFalse(AutoVariableSynthesizer.isValidDomainVariableName(""));
        assertFalse(AutoVariableSynthesizer.isValidDomainVariableName(null));
        assertFalse(AutoVariableSynthesizer.isValidDomainVariableName("PascalCaseVar"));
    }
}
