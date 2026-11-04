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
        RIGHT_TO_LEFT,
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
        return findTargetByLabel(labelHint, allNodes, null, null);
    }

    /**
     * Action- and Tag-aware spatial resolver: locates the reference label and resolves the best
     * aligned input field (EditText), checkbox (CheckBox / compact ViewGroup to the left/right), or value node.
     */
    public Optional<UIElementNode> findTargetByLabel(String labelHint, List<UIElementNode> allNodes, String action, String expectedTag) {
        if (labelHint == null || allNodes == null || allNodes.isEmpty()) {
            return Optional.empty();
        }

        // 1. Locate the best matching reference label (prefer non-input text labels)
        UIElementNode bestLabel = null;
        double bestSim = 0.0;

        for (UIElementNode node : allNodes) {
            if (!node.isDisplayed()) continue;
            if (node.isInputWidget()) continue;
            if (node.hasText()) {
                double sim = Math.max(
                        StringSimilarity.compositeScore(node.getText(), labelHint),
                        StringSimilarity.tokenOverlap(node.getText(), labelHint));
                if (sim > bestSim && sim >= 0.60) {
                    bestSim = sim;
                    bestLabel = node;
                }
            } else if (node.hasContentDesc()) {
                double sim = Math.max(
                        StringSimilarity.compositeScore(node.getContentDesc(), labelHint),
                        StringSimilarity.tokenOverlap(node.getContentDesc(), labelHint));
                if (sim > bestSim && sim >= 0.60) {
                    bestSim = sim;
                    bestLabel = node;
                }
            }
        }

        if (bestLabel == null) {
            return Optional.empty();
        }

        // 2. Find closest aligned value / input / checkbox for this label
        return findAlignedNode(bestLabel, allNodes, AlignmentMode.AUTO_BEST_PROXIMITY, action, expectedTag, labelHint);
    }

    /**
     * Finds the single best aligned candidate for a given reference node.
     */
    public Optional<UIElementNode> findAlignedNode(UIElementNode ref, List<UIElementNode> allNodes, AlignmentMode mode) {
        return findAlignedNode(ref, allNodes, mode, null, null, null);
    }

    public Optional<UIElementNode> findAlignedNode(UIElementNode ref, List<UIElementNode> allNodes, AlignmentMode mode,
                                                   String action, String expectedTag, String labelHint) {
        if (ref == null || ref.getBounds() == null || allNodes == null) return Optional.empty();

        Rectangle rBounds = ref.getBounds();
        int rX1 = rBounds.x;
        int rY1 = rBounds.y;
        int rX2 = rBounds.x + rBounds.width;
        int rY2 = rBounds.y + rBounds.height;

        String expLower = expectedTag != null ? expectedTag.toLowerCase() : "";
        boolean isInputIntent = ("SET_TEXT".equalsIgnoreCase(action) || "INPUT".equalsIgnoreCase(action) ||
                "CLEAR_TEXT".equalsIgnoreCase(action) || "FILL".equalsIgnoreCase(action)) ||
                expLower.contains("edittext") || expLower.contains("textfield") || expLower.contains("input");
        boolean isCheckboxIntent = expLower.contains("checkbox") || expLower.contains("radio") ||
                expLower.contains("switch") || isCheckboxSemanticHint(labelHint);

        UIElementNode bestCandidate = null;
        double bestScore = 0.0;

        for (UIElementNode cand : allNodes) {
            if (cand == ref || !cand.isDisplayed() || cand.getBounds() == null) continue;
            // Do not pick an ancestor that wraps the reference label itself unless no sibling/child exists
            if (isAncestorOf(cand, ref)) continue;

            Rectangle cBounds = cand.getBounds();
            int cX1 = cBounds.x;
            int cY1 = cBounds.y;
            int cX2 = cBounds.x + cBounds.width;
            int cY2 = cBounds.y + cBounds.height;

            // Ignore 0-sized elements
            if (cBounds.width <= 0 || cBounds.height <= 0) continue;

            // Prioritize interactive, checkable, input, or text nodes
            boolean isInteractive = cand.isClickable() || cand.isCheckable() || cand.isInputWidget() ||
                    cand.isCheckboxOrToggleWidget() || cand.isCompactCheckboxGeometry() || cand.hasText();
            if (!isInteractive) continue;

            // Widget Specificity Adjustment (prevents wrapper ViewGroup from stealing EditText or CheckBox)
            double specificityBonus = 0.0;
            if (isInputIntent) {
                if (cand.isInputWidget()) {
                    specificityBonus = 0.35;
                } else if (cand.getTag().toLowerCase().contains("viewgroup")) {
                    specificityBonus = -0.10;
                }
            } else if (isCheckboxIntent) {
                if (cand.isCheckboxOrToggleWidget()) {
                    specificityBonus = 0.35;
                } else if (cand.isCompactCheckboxGeometry()) {
                    specificityBonus = 0.30;
                }
            }

            if (mode == AlignmentMode.LEFT_TO_RIGHT || mode == AlignmentMode.AUTO_BEST_PROXIMITY) {
                // Left-to-Right: Cand is to the right of Ref
                if (cX1 >= rX1 && cX2 > rX2) {
                    int verticalOverlap = Math.max(0, Math.min(rY2, cY2) - Math.max(rY1, cY1));
                    int minHeight = Math.max(1, Math.min(rBounds.height, cBounds.height));
                    double verticalOverlapRatio = (double) verticalOverlap / minHeight;

                    if (verticalOverlapRatio >= 0.40) {
                        int hDist = Math.max(0, cX1 - rX2);
                        double distanceScore = 1.0 / (1.0 + (hDist / 100.0));
                        double totalScore = (verticalOverlapRatio * 0.6) + (distanceScore * 0.4) + specificityBonus;

                        if (isBetterCandidate(totalScore, bestScore, cand, bestCandidate)) {
                            bestScore = totalScore;
                            bestCandidate = cand;
                        }
                    }
                }
            }

            if (mode == AlignmentMode.RIGHT_TO_LEFT ||
                    (mode == AlignmentMode.AUTO_BEST_PROXIMITY && (isCheckboxIntent || cand.isCheckboxOrToggleWidget() || cand.isCompactCheckboxGeometry()))) {
                // Right-to-Left: Cand is to the LEFT of Ref (Standard mobile CheckBox / Custom ViewGroup Checkbox layout)
                if (cX2 <= rX1 + 30 && cX1 < rX1) {
                    int verticalOverlap = Math.max(0, Math.min(rY2, cY2) - Math.max(rY1, cY1));
                    int minHeight = Math.max(1, Math.min(rBounds.height, cBounds.height));
                    double verticalOverlapRatio = (double) verticalOverlap / minHeight;
                    boolean centerAligned = Math.abs(cand.getCenterY() - ref.getCenterY()) <= 60;

                    if (verticalOverlapRatio >= 0.30 || centerAligned) {
                        int hDist = Math.max(0, rX1 - cX2);
                        if (hDist <= 240) {
                            double vScore = Math.max(verticalOverlapRatio, centerAligned ? 0.85 : 0.0);
                            double distanceScore = 1.0 / (1.0 + (hDist / 80.0));
                            double checkBonus = (cand.isCheckboxOrToggleWidget() || cand.isCompactCheckboxGeometry()) ? 0.25 : 0.0;
                            double totalScore = (vScore * 0.55) + (distanceScore * 0.45) + specificityBonus + checkBonus;

                            if (isBetterCandidate(totalScore, bestScore, cand, bestCandidate)) {
                                bestScore = totalScore;
                                bestCandidate = cand;
                            }
                        }
                    }
                }
            }

            if (mode == AlignmentMode.TOP_TO_BOTTOM || mode == AlignmentMode.AUTO_BEST_PROXIMITY) {
                // Top-to-Bottom: Cand is below Ref
                if (cY1 >= rY1 && cY2 > rY2) {
                    int horizontalOverlap = Math.max(0, Math.min(rX2, cX2) - Math.max(rX1, cX1));
                    int minWidth = Math.max(1, Math.min(rBounds.width, cBounds.width));
                    double horizontalOverlapRatio = (double) horizontalOverlap / minWidth;

                    if (horizontalOverlapRatio >= 0.30) {
                        int vDist = Math.max(0, cY1 - rY2);
                        if (vDist <= 350) { // Max vertical proximity
                            double distanceScore = 1.0 / (1.0 + (vDist / 80.0));
                            double totalScore = (horizontalOverlapRatio * 0.5) + (distanceScore * 0.5) + specificityBonus;

                            if (isBetterCandidate(totalScore, bestScore, cand, bestCandidate)) {
                                bestScore = totalScore;
                                bestCandidate = cand;
                            }
                        }
                    }
                }
            }
        }

        // Automatic Child Input Descent: If input was requested and a wrapper ViewGroup won, descend to its child EditText
        if (bestCandidate != null && isInputIntent && !bestCandidate.isInputWidget()) {
            UIElementNode childInput = findDescendantInput(bestCandidate);
            if (childInput != null) {
                bestCandidate = childInput;
            }
        }

        return Optional.ofNullable(bestCandidate);
    }

    private boolean isBetterCandidate(double newScore, double currentBestScore, UIElementNode cand, UIElementNode currentBest) {
        if (currentBest == null) return true;
        if (newScore > currentBestScore + 0.001) return true;
        if (Math.abs(newScore - currentBestScore) <= 0.001) {
            if (cand.isInputWidget() && !currentBest.isInputWidget()) return true;
            if (cand.isCheckboxOrToggleWidget() && !currentBest.isCheckboxOrToggleWidget()) return true;
            return cand.getDepth() > currentBest.getDepth();
        }
        return false;
    }

    private boolean isAncestorOf(UIElementNode potentialAncestor, UIElementNode node) {
        if (potentialAncestor == null || node == null) return false;
        UIElementNode curr = node.getParent();
        while (curr != null) {
            if (curr == potentialAncestor) return true;
            curr = curr.getParent();
        }
        return false;
    }

    private UIElementNode findDescendantInput(UIElementNode container) {
        if (container == null) return null;
        for (UIElementNode child : container.getChildren()) {
            if (child.isDisplayed() && child.isInputWidget()) {
                return child;
            }
            UIElementNode nested = findDescendantInput(child);
            if (nested != null) return nested;
        }
        return null;
    }

    public static boolean isCheckboxSemanticHint(String hint) {
        if (hint == null || hint.isEmpty()) return false;
        String h = hint.toLowerCase();
        return h.contains("setuju") || h.contains("syarat") || h.contains("ketentuan") ||
               h.contains("snk") || h.contains("s&k") || h.contains("centang") ||
               h.contains("checkbox") || h.contains("check box") || h.contains("agree") ||
               h.contains("terms") || h.contains("ingat saya") || h.contains("remember");
    }

    /**
     * Automatically extracts all visually paired (Label -> Value) elements on the active screen.
     */
    public List<SpatialPair> extractAllPairs(List<UIElementNode> allNodes) {
        List<SpatialPair> pairs = new ArrayList<>();
        if (allNodes == null || allNodes.isEmpty()) return pairs;

        // Collect all potential non-button nodes with valid bounds
        List<UIElementNode> candidateNodes = new ArrayList<>();
        List<UIElementNode> labelNodes = new ArrayList<>();
        for (UIElementNode node : allNodes) {
            if (!node.isDisplayed() || node.getBounds() == null || node.getBounds().width <= 0) continue;
            String tagLower = node.getTag() != null ? node.getTag().toLowerCase() : "";
            if (tagLower.contains("button")) continue;
            candidateNodes.add(node);
            if (node.hasText() && !node.getText().trim().isEmpty()) {
                labelNodes.add(node);
            }
        }

        Set<UIElementNode> claimedValues = new HashSet<>();

        for (UIElementNode label : labelNodes) {
            if (claimedValues.contains(label)) continue;

            Optional<UIElementNode> matchedVal = findAlignedNode(label, candidateNodes, AlignmentMode.LEFT_TO_RIGHT);
            AlignmentMode usedMode = AlignmentMode.LEFT_TO_RIGHT;

            if (!matchedVal.isPresent()) {
                // Check tight vertical label-above-value stacking (common in mobile banking confirmation cards)
                Rectangle rBounds = label.getBounds();
                int rX1 = rBounds.x;
                int rY2 = rBounds.y + rBounds.height;
                for (UIElementNode cand : candidateNodes) {
                    if (cand == label || claimedValues.contains(cand) || !cand.hasText()) continue;
                    Rectangle cBounds = cand.getBounds();
                    int vDist = cBounds.y - rY2;
                    if (vDist >= 0 && vDist <= 40 && Math.abs(cBounds.x - rX1) <= 40) {
                        matchedVal = Optional.of(cand);
                        usedMode = AlignmentMode.TOP_TO_BOTTOM;
                        break;
                    }
                }
            }

            if (matchedVal.isPresent()) {
                UIElementNode val = matchedVal.get();
                if (!claimedValues.contains(val)) {
                    claimedValues.add(val);
                    pairs.add(new SpatialPair(label, val, usedMode, 0.90));
                }
            }
        }

        return pairs;
    }
}
