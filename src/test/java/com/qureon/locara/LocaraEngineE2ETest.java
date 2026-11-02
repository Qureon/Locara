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
        File auditJson = new File(tempProjectDir.toFile(), "Include/evidence/healing_audit.json");
        if (!auditJson.exists()) {
            auditJson = new File(tempProjectDir.toFile(), "Include/evidence/locara_audit.json");
        }
        assertTrue(auditJson.exists(), "Audit ledger file should be created");
        String auditContent = Files.readString(auditJson.toPath());
        assertTrue(auditContent.contains("SUCCESS"));
        assertTrue(auditContent.contains("Lanjut Transfer"));
    }

    @Test
    @DisplayName("Should heal MPIN input field for SET_TEXT action")
    void testHealMpinInput() {
        HealConfig config = new HealConfig();
        config.setKatalonProjectPath(tempProjectDir.toString());
        config.setMinConfidenceThreshold(0.70);

        LocaraEngine engine = new LocaraEngine(config);

        HealContext context = HealContext.builder()
                .targetHint("Masukkan M-PIN")
                .action("SET_TEXT")
                .value("123456")
                .screenName("TransferConfirmation")
                .targetRsPath("Object Repository/M-Smile/Transfer/input_MPIN")
                .build();

        HealResult result = engine.heal(sampleXml, context);

        assertTrue(result.isSuccess());
        assertNotNull(result.getCandidate());
        assertEquals("com.bankmega.msmile:id/et_mpin", result.getCandidate().getNode().getResourceId());
    }

    @Test
    @DisplayName("Should reject unrelated target hint with confidence below threshold")
    void testRejectUnrelatedTarget() {
        HealConfig config = new HealConfig();
        config.setKatalonProjectPath(tempProjectDir.toString());
        config.setMinConfidenceThreshold(0.80);

        LocaraEngine engine = new LocaraEngine(config);

        HealContext context = HealContext.builder()
                .targetHint("Download Bukti Transaksi PDF")
                .action("TAP")
                .screenName("TransferConfirmation")
                .build();

        HealResult result = engine.heal(sampleXml, context);

        assertFalse(result.isSuccess());
        assertNull(result.getCandidate());
        assertTrue(result.getMessage().contains("No element matched hint"));
    }

    @Test
    @DisplayName("Should strictly preserve existing .rs files and write to .healed.rs (Strict Anti-Tampering Invariant)")
    void testAntiTamperingPreservesExistingRsFile() throws Exception {
        // Pre-create original spied .rs file
        File objRepoDir = new File(tempProjectDir.toFile(), "Object Repository/M-Smile/Transfer");
        objRepoDir.mkdirs();
        File originalRs = new File(objRepoDir, "btn_Lanjut.rs");
        String originalContent = "<!-- ORIGINAL PRISTINE RS FILE -->";
        Files.writeString(originalRs.toPath(), originalContent, StandardCharsets.UTF_8);

        HealConfig config = new HealConfig();
        config.setKatalonProjectPath(tempProjectDir.toString());
        config.setAutoGenerateRs(true);
        config.setAllowOverwriteExistingRs(false); // Strict Invariant: never overwrite!
        config.setMinConfidenceThreshold(0.75);

        LocaraEngine engine = new LocaraEngine(config);

        HealContext context = HealContext.builder()
                .targetHint("Lanjut Transfer")
                .action("TAP")
                .screenName("TransferConfirmation")
                .targetRsPath("Object Repository/M-Smile/Transfer/btn_Lanjut")
                .build();

        HealResult result = engine.heal(sampleXml, context);

        assertTrue(result.isSuccess());

        // 1. Verify original spied file was NEVER modified
        String currentOriginalContent = Files.readString(originalRs.toPath(), StandardCharsets.UTF_8);
        assertEquals(originalContent, currentOriginalContent, "Original .rs file must remain pristine and untouched!");

        // 2. Verify safe quarantine file was created
        File healedRs = result.getGeneratedRsFile();
        assertNotNull(healedRs);
        assertTrue(healedRs.exists());
        assertTrue(healedRs.getName().endsWith(".healed.rs"), "Must write to .healed.rs to prevent tampering!");
    }
}
