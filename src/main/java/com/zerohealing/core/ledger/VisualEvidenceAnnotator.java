package com.zerohealing.core.ledger;

import com.zerohealing.core.model.HealCandidate;
import com.zerohealing.core.model.HealConfig;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Visual evidence generator that annotates screenshots with high-contrast
 * bounding boxes and confidence badges on healed mobile elements.
 */
public class VisualEvidenceAnnotator {
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public VisualEvidenceAnnotator() {}

    /**
     * Reads the raw screenshot, draws a highlighted bounding box with confidence badge,
     * and writes the annotated PNG file to the evidence directory.
     */
    public File annotateScreenshot(File rawScreenshot, HealCandidate candidate, HealConfig config) {
        if (rawScreenshot == null || !rawScreenshot.exists() || candidate == null) {
            return null;
        }

        try {
            BufferedImage image = ImageIO.read(rawScreenshot);
            if (image == null) return null;

            annotateBufferedImage(image, candidate);

            File evidenceDir = new File(config.getKatalonProjectPath(), config.getAuditLogFolder());
            if (!evidenceDir.exists()) {
                evidenceDir.mkdirs();
            }

            String filename = "healed_" + LocalDateTime.now().format(FILE_FMT) + "_" + (System.currentTimeMillis() % 1000) + ".png";
            File outputFile = new File(evidenceDir, filename);
            ImageIO.write(image, "png", outputFile);

            return outputFile;
        } catch (IOException e) {
            return null;
        }
    }

    public void annotateBufferedImage(BufferedImage image, HealCandidate candidate) {
        if (image == null || candidate == null) return;

        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Rectangle bounds = candidate.getNode().getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) {
            g2d.dispose();
            return;
        }

        // Draw vibrant bounding box outline
        g2d.setColor(new Color(255, 68, 68, 230)); // High-visibility Coral/Red
        g2d.setStroke(new BasicStroke(5.0f));
        g2d.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12);

        // Subtle highlight fill
        g2d.setColor(new Color(255, 68, 68, 35));
        g2d.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12);

        // Badge Text
        String identifier = candidate.getNode().getStrippedResourceId();
        if (identifier.isEmpty()) {
            identifier = candidate.getNode().getText();
        }
        if (identifier.isEmpty()) {
            identifier = candidate.getNode().getTag();
        }

        String badgeText = String.format("HEALED (%.0f%%): %s",
                candidate.getConfidenceScore() * 100.0,
                identifier);

        Font font = new Font("SansSerif", Font.BOLD, 22);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(badgeText);
        int textHeight = fm.getHeight();

        int badgeX = Math.max(0, bounds.x);
        int badgeY = Math.max(textHeight + 12, bounds.y - 12);

        // Badge Container Background
        g2d.setColor(new Color(20, 20, 20, 220));
        g2d.fillRoundRect(badgeX, badgeY - textHeight, textWidth + 24, textHeight + 8, 10, 10);

        // Badge Border
        g2d.setColor(new Color(255, 68, 68));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRoundRect(badgeX, badgeY - textHeight, textWidth + 24, textHeight + 8, 10, 10);

        // Badge Text Fill
        g2d.setColor(Color.WHITE);
        g2d.drawString(badgeText, badgeX + 12, badgeY - 6);

        g2d.dispose();
    }
}
