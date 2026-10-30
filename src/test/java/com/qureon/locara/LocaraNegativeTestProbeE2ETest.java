package com.qureon.locara;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class LocaraNegativeTestProbeE2ETest {

    @TempDir
    Path tempProjectDir;

    private static final String NEGATIVE_SCREEN_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<hierarchy rotation=\"0\">\n" +
            "  <android.widget.FrameLayout bounds=\"[0,0][1440,2960]\">\n" +
            "    <android.widget.LinearLayout bounds=\"[0,0][1440,2960]\">\n" +
            "      <android.widget.TextView text=\"Input Nomor Pelanggan Baru\" bounds=\"[188,376][631,421]\" />\n" +
            "      <android.widget.EditText text=\"0812345\" bounds=\"[65,430][1215,510]\" />\n" +
            "      <android.widget.TextView text=\"No handphone tidak valid karena kurang dari 10 digit\" bounds=\"[65,526][1215,575]\" />\n" +
            "      <android.widget.Button text=\"Lanjut\" bounds=\"[560,2539][721,2605]\" />\n" +
            "    </android.widget.LinearLayout>\n" +
            "  </android.widget.FrameLayout>\n" +
            "</hierarchy>";

    @Test
    @DisplayName("Locara should heal negative test probe with VERIFY_PRESENT action")
    void testNegativeTestProbeHealing() {
        HealConfig config = new HealConfig();
        config.setKatalonProjectPath(tempProjectDir.toString());
        config.setAutoGenerateRs(false);
        config.setMinConfidenceThreshold(0.70);

        LocaraEngine engine = new LocaraEngine(config);

        HealContext context = HealContext.builder()
                .targetHint("No handphone tidak valid karena kurang dari 10 digit")
                .action("VERIFY_PRESENT")
                .screenName("ERROR_SCREEN")
                .targetRsPath("Object Repository/ERROR SCREEN/HP - Kurang Dari 10 Digit/00. TextView - No handphone tidak valid karena kurang dari 10 digit")
                .originalLocator("//hierarchy/android.widget.FrameLayout[1]/android.widget.LinearLayout[1]/android.widget.TextView[2]")
                .build();

        HealResult result = engine.heal(NEGATIVE_SCREEN_XML, context);

        assertTrue(result.isSuccess(), "Probe healing must succeed for inline negative error message");
        assertNotNull(result.getCandidate());
        assertTrue(result.getCandidate().getConfidenceScore() >= 0.80);
        assertEquals("No handphone tidak valid karena kurang dari 10 digit", result.getCandidate().getNode().getText());
        assertTrue(result.getCandidate().getSuggestedXPath().contains("kurang dari 10 digit"));
    }
}
