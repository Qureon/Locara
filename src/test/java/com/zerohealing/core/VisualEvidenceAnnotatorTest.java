package com.zerohealing.core;

import com.zerohealing.core.ledger.VisualEvidenceAnnotator;
import com.zerohealing.core.model.HealCandidate;
import com.zerohealing.core.model.HealConfig;
import com.zerohealing.core.model.UIElementNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class VisualEvidenceAnnotatorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should successfully annotate screenshot with bounding box and badge")
    void testAnnotateScreenshot() throws Exception {
        // Create dummy screenshot image
        BufferedImage rawImg = new BufferedImage(1080, 2400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rawImg.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, 1080, 2400);
        g.dispose();

        File rawFile = new File(tempDir.toFile(), "raw_screenshot.png");
        ImageIO.write(rawImg, "png", rawFile);

        // Build candidate
        UIElementNode node = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_next",
                "Lanjut",
                "Lanjut Transfer",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(540, 2150, 480, 140),
                true,
                true,
                true,
                false,
                false,
                3
        );

        HealCandidate candidate = new HealCandidate(
                node,
                0.94,
                0.95,
                0.92,
                0.90,
                1.0,
                1.0,
                "//android.widget.Button[@text='Lanjut']",
                "High text match"
        );

        HealConfig config = new HealConfig();
        config.setKatalonProjectPath(tempDir.toString());
        config.setAuditLogFolder("evidence");

        VisualEvidenceAnnotator annotator = new VisualEvidenceAnnotator();
        File annotatedFile = annotator.annotateScreenshot(rawFile, candidate, config);

        assertNotNull(annotatedFile);
        assertTrue(annotatedFile.exists());
        assertTrue(annotatedFile.length() > 0);

        BufferedImage resultImg = ImageIO.read(annotatedFile);
        assertNotNull(resultImg);
        assertEquals(1080, resultImg.getWidth());
        assertEquals(2400, resultImg.getHeight());
    }
}
