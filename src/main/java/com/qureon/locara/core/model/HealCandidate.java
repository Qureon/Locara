package com.qureon.locara.core.model;

import java.util.Objects;

/**
 * Encapsulates a candidate UIElementNode evaluated by the Heuristic Engine,
 * complete with individual vector scores and aggregate confidence.
 */
public class HealCandidate implements Comparable<HealCandidate> {
    private final UIElementNode node;
    private final double confidenceScore;
    private final double textScore;
    private final double idScore;
    private final double descScore;
    private final double classScore;
    private final double proximityScore;
    private final String suggestedXPath;
    private final String healReason;

    public HealCandidate(UIElementNode node,
                         double confidenceScore,
                         double textScore,
                         double idScore,
                         double descScore,
                         double classScore,
                         double proximityScore,
                         String suggestedXPath,
                         String healReason) {
        this.node = node;
        this.confidenceScore = confidenceScore;
        this.textScore = textScore;
        this.idScore = idScore;
        this.descScore = descScore;
        this.classScore = classScore;
        this.proximityScore = proximityScore;
        this.suggestedXPath = suggestedXPath != null ? suggestedXPath : "";
        this.healReason = healReason != null ? healReason : "";
    }

    public HealCandidate(UIElementNode node, String suggestedXPath, double confidenceScore, String healReason) {
        this(node, confidenceScore, 0.0, 0.0, 0.0, 0.0, 0.0, suggestedXPath, healReason);
    }

    public UIElementNode getNode() { return node; }
    public double getConfidenceScore() { return confidenceScore; }
    public double getTextScore() { return textScore; }
    public double getIdScore() { return idScore; }
    public double getDescScore() { return descScore; }
    public double getClassScore() { return classScore; }
    public double getProximityScore() { return proximityScore; }
    public String getSuggestedXPath() { return suggestedXPath; }
    public String getHealReason() { return healReason; }

    public boolean isConfident(double threshold) {
        return confidenceScore >= threshold;
    }

    @Override
    public int compareTo(HealCandidate other) {
        // Sort descending by confidence score
        return Double.compare(other.confidenceScore, this.confidenceScore);
    }

    @Override
    public String toString() {
        return String.format("HealCandidate{confidence=%.2f%%, id='%s', text='%s', xpath='%s', reason='%s'}",
                confidenceScore * 100.0,
                node.getStrippedResourceId(),
                node.getText(),
                suggestedXPath,
                healReason);
    }
}
