package com.qureon.locara;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class LocaraEngineE2ETest {

    @TempDir
    Path tempProjectDir;

    private String sampleXml;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/samples/msmile_transfer_confirm.xml")) {
            assertNotNull(is, "Sample XML fixture must exist");
            sampleXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Locara by Qureon should heal missing elements and write genuine MobileElementEntity .rs file")
    void testLocaraHealingFlow() throws Exception {
        HealConfig config = new HealConfig();
        config.setKatalonProjectPath(tempProjectDir.toString());
        config.setAutoGenerateRs(true);
        config.setMinConfidenceThreshold(0.70);

        LocaraEngine engine = new LocaraEngine(config);

        HealContext context = HealContext.builder()
                .targetHint("Lanjut Transfer")
                .action("TAP")
                .screenName("TransferConfirmation")
                .targetRsPath("Object Repository/Dashboard/btn_Lanjut")
                .build();

        HealResult result = engine.heal(sampleXml, context);

        assertTrue(result.isSuccess(), "Locara healing should succeed");
        assertNotNull(result.getCandidate());
        assertTrue(result.getCandidate().getConfidenceScore() >= 0.70);

        File generatedRs = result.getGeneratedRsFile();
        assertNotNull(generatedRs);
        assertTrue(generatedRs.exists());

        String rsXml = Files.readString(generatedRs.toPath());
        assertTrue(rsXml.contains("<MobileElementEntity>"));
        assertTrue(rsXml.contains("Locara by Qureon Engine"));
        assertTrue(rsXml.contains("<locatorStrategy>ACCESSIBILITY</locatorStrategy>"));
        assertTrue(rsXml.contains("<locator>Tombol Lanjut Transfer</locator>"));

        // Verify locara audit ledger file
        File auditJson = new File(tempProjectDir.toFile(), "Include/evidence/locara_audit.json");
        assertTrue(auditJson.exists(), "locara_audit.json should be created");
        String auditContent = Files.readString(auditJson.toPath());
        assertTrue(auditContent.contains("SUCCESS"));
        assertTrue(auditContent.contains("Lanjut Transfer"));
    }
}
