package com.qureon.locara.core.sanitizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class LocaraOrSanitizerTest {

    @TempDir
    Path tempDir;

    private static final String BRITTLE_RS_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<MobileElementEntity>\n" +
            "   <description></description>\n" +
            "   <name>00. TextView - No handphone tidak valid karena kurang dari 10 digit</name>\n" +
            "   <tag></tag>\n" +
            "   <webElementProperties>\n" +
            "      <isSelected>false</isSelected>\n" +
            "      <matchCondition>equals</matchCondition>\n" +
            "      <name>text</name>\n" +
            "      <type>Main</type>\n" +
            "      <value>No handphone tidak valid karena kurang dari 10 digit</value>\n" +
            "   </webElementProperties>\n" +
            "   <webElementProperties>\n" +
            "      <isSelected>true</isSelected>\n" +
            "      <matchCondition>equals</matchCondition>\n" +
            "      <name>xpath</name>\n" +
            "      <type>Main</type>\n" +
            "      <value>//hierarchy/android.widget.FrameLayout[1]/android.widget.TextView[1]</value>\n" +
            "   </webElementProperties>\n" +
            "   <locator>//hierarchy/android.widget.FrameLayout[1]/android.widget.TextView[1]</locator>\n" +
            "   <locatorStrategy>XPATH</locatorStrategy>\n" +
            "</MobileElementEntity>";

    @Test
    @DisplayName("LocaraOrSanitizer should detect and sanitize brittle hierarchy XPath in .rs file")
    void testSanitizeBrittleRs() throws Exception {
        File rsFile = new File(tempDir.toFile(), "test_error.rs");
        Files.writeString(rsFile.toPath(), BRITTLE_RS_XML);

        LocaraOrSanitizer sanitizer = new LocaraOrSanitizer();
        LocaraOrSanitizer.SanitizeSummary summary = sanitizer.scanAndSanitize(tempDir.toFile(), true, true);

        assertEquals(1, summary.getTotalScanned());
        assertEquals(1, summary.getBrittleFound());
        assertEquals(1, summary.getTotalSanitized());

        // Verify updated file content
        String updated = Files.readString(rsFile.toPath());
        assertFalse(updated.contains("<locator>//hierarchy"), "Should remove brittle hierarchy from locator tag");
        assertTrue(updated.contains("<locator>//*[contains(@text, 'No handphone tidak valid karena kurang dari 10 digit')"),
                "Should synthesize resilient relative XPath containing error text");

        // Verify backup was created
        File backupFile = new File(rsFile.getAbsolutePath() + ".bak");
        assertTrue(backupFile.exists(), "Backup file (.bak) should be generated");
    }
}
