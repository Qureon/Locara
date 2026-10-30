package com.qureon.locara.core.spatial;

import com.qureon.locara.core.matcher.StringSimilarity;
import com.qureon.locara.core.model.UIElementNode;

import java.awt.Rectangle;
import java.util.*;

/**
 * 📐 SpatialAlignmentEngine — Universal 2D Geometry & Spatial Proximity Resolver.
 *
 * Infers structural and functional relationships between UI elements based on 2D visual layout.
 * Resolves left-to-right (label -> value) and top-to-bottom (label -> input field) pairings
 * completely independent of DOM nesting depth or framework type.
 */
public class SpatialAlignmentEngine {

    public enum AlignmentMode {
        LEFT_TO_RIGHT,
        TOP_TO_BOTTOM,
        AUTO_BEST_PROXIMITY
    }

    public static class SpatialPair {
        private final UIElementNode labelNode;
        private final UIElementNode valueNode;
        private final AlignmentMode mode;
        private final double alignmentScore;

        public SpatialPair(UIElementNode labelNode, UIElementNode valueNode, AlignmentMode mode, double alignmentScore) {
            this.labelNode = labelNode;
            this.valueNode = valueNode;
            this.mode = mode;
            this.alignmentScore = alignmentScore;
        }

        public UIElementNode getLabelNode() { return labelNode; }
        public UIElementNode getValueNode() { return valueNode; }
        public AlignmentMode getMode() { return mode; }
        public double getAlignmentScore() { return alignmentScore; }

        @Override
        public String toString() {
            String lText = labelNode.hasText() ? labelNode.getText() : labelNode.getContentDesc();
            String vText = valueNode.hasText() ? valueNode.getText() : valueNode.getContentDesc();
            return String.format(Locale.US, "SpatialPair['%s' -> '%s' (%s, Score: %.2f)]",
                    lText, vText != null ? vText : valueNode.getTag(), mode, alignmentScore);
        }
    }

    /**
     * Discovers the corresponding input field (EditText, clickable container) or value node
     * associated with a semantic label hint.
     */
    public Optional<UIElementNode> findTargetByLabel(String labelHint, List<UIElementNode> allNodes) {
        if (labelHint == null || allNodes == null || allNodes.isEmpty()) {
            return Optional.empty();
        }

        // 1. Locate the best matching reference label
        UIElementNode bestLabel = null;
        double bestSim = 0.0;

        for (UIElementNode node : allNodes) {
            if (node.hasText()) {
                double sim = StringSimilarity.compositeScore(node.getText(), labelHint);
                if (sim > bestSim && sim >= 0.70) {
                    bestSim = sim;
                    bestLabel = node;
                }
            } else if (node.hasContentDesc()) {
                double sim = StringSimilarity.compositeScore(node.getContentDesc(), labelHint);
                if (sim > bestSim && sim >= 0.70) {
                    bestSim = sim;
                    bestLabel = node;
                }
            }
        }

        if (bestLabel == null) {
            return Optional.empty();
        }

        // 2. Find closest aligned value / input for this label
        return findAlignedNode(bestLabel, allNodes, AlignmentMode.AUTO_BEST_PROXIMITY);
    }

    /**
     * Finds the single best aligned candidate for a given reference node.
     */
    public Optional<UIElementNode> findAlignedNode(UIElementNode ref, List<UIElementNode> allNodes, AlignmentMode mode) {
        if (ref == null || ref.getBounds() == null || allNodes == null) return Optional.empty();

        Rectangle rBounds = ref.getBounds();
        int rX1 = rBounds.x;
        int rY1 = rBounds.y;
        int rX2 = rBounds.x + rBounds.width;
        int rY2 = rBounds.y + rBounds.height;

        UIElementNode bestCandidate = null;
        double bestScore = 0.0;

        for (UIElementNode cand : allNodes) {
            if (cand == ref || cand.getBounds() == null) continue;

            Rectangle cBounds = cand.getBounds();
            int cX1 = cBounds.x;
            int cY1 = cBounds.y;
            int cX2 = cBounds.x + cBounds.width;
            int cY2 = cBounds.y + cBounds.height;

            // Ignore 0-sized elements
            if (cBounds.width <= 0 || cBounds.height <= 0) continue;

            // Prioritize interactive or text nodes
            boolean isInteractive = cand.isClickable() || cand.getTag().toLowerCase().contains("edit") || cand.hasText();
            if (!isInteractive) continue;

            if (mode == AlignmentMode.LEFT_TO_RIGHT || mode == AlignmentMode.AUTO_BEST_PROXIMITY) {
                // Left-to-Right: Cand must be to the right of Ref
                if (cX1 >= rX1 && cX2 > rX2) {
                    int verticalOverlap = Math.max(0, Math.min(rY2, cY2) - Math.max(rY1, cY1));
                    int minHeight = Math.max(1, Math.min(rBounds.height, cBounds.height));
                    double verticalOverlapRatio = (double) verticalOverlap / minHeight;

                    if (verticalOverlapRatio >= 0.40) {
                        int hDist = Math.max(0, cX1 - rX2);
                        double distanceScore = 1.0 / (1.0 + (hDist / 100.0));
                        double totalScore = (verticalOverlapRatio * 0.6) + (distanceScore * 0.4);

                        if (totalScore > bestScore) {
                            bestScore = totalScore;
                            bestCandidate = cand;
                        }
                    }
                }
            }

            if (mode == AlignmentMode.TOP_TO_BOTTOM || mode == AlignmentMode.AUTO_BEST_PROXIMITY) {
                // Top-to-Bottom: Cand must be below Ref
                if (cY1 >= rY1 && cY2 > rY2) {
                    int horizontalOverlap = Math.max(0, Math.min(rX2, cX2) - Math.max(rX1, cX1));
                    int minWidth = Math.max(1, Math.min(rBounds.width, cBounds.width));
                    double horizontalOverlapRatio = (double) horizontalOverlap / minWidth;

                    if (horizontalOverlapRatio >= 0.30) {
                        int vDist = Math.max(0, cY1 - rY2);
                        if (vDist <= 350) { // Max vertical proximity
                            double distanceScore = 1.0 / (1.0 + (vDist / 80.0));
                            double totalScore = (horizontalOverlapRatio * 0.5) + (distanceScore * 0.5);

                            if (totalScore > bestScore) {
                                bestScore = totalScore;
                                bestCandidate = cand;
                            }
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(bestCandidate);
    }

    /**
     * Automatically extracts all visually paired (Label -> Value) elements on the active screen.
     */
    public List<SpatialPair> extractAllPairs(List<UIElementNode> allNodes) {
        List<SpatialPair> pairs = new ArrayList<>();
        if (allNodes == null || allNodes.isEmpty()) return pairs;

        // Collect all potential label nodes (nodes with non-empty text)
        List<UIElementNode> labelNodes = new ArrayList<>();
        for (UIElementNode node : allNodes) {
            if (node.hasText() && !node.getText().trim().isEmpty() && node.getBounds().width > 0) {
                labelNodes.add(node);
            }
        }

        Set<UIElementNode> claimedValues = new HashSet<>();

        for (UIElementNode label : labelNodes) {
            Optional<UIElementNode> matchedVal = findAlignedNode(label, allNodes, AlignmentMode.LEFT_TO_RIGHT);
            if (matchedVal.isPresent()) {
                UIElementNode val = matchedVal.get();
                if (!claimedValues.contains(val)) {
                    claimedValues.add(val);
                    pairs.add(new SpatialPair(label, val, AlignmentMode.LEFT_TO_RIGHT, 0.90));
                }
            }
        }

        return pairs;
    }
}
