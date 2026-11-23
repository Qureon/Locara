package com.qureon.locara.core.spatial;

import com.qureon.locara.core.matcher.StringSimilarity;
import com.qureon.locara.core.model.UIElementNode;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * SpatialAlignmentEngine — Universal 2D Geometry & Spatial Proximity Resolver.
 */
public class SpatialAlignmentEngine {

    private static final double MIN_LABEL_SIMILARITY = 0.60;

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

    public Optional<UIElementNode> findTargetByLabel(String labelHint, List<UIElementNode> allNodes) {
        return findTargetByLabel(labelHint, allNodes, null, null);
    }

    public Optional<UIElementNode> findTargetByLabel(String labelHint, List<UIElementNode> allNodes, String action, String expectedTag) {
        if (labelHint == null || allNodes == null || allNodes.isEmpty()) return Optional.empty();

        UIElementNode bestLabel = null;
        double bestSim = 0.0;
        for (UIElementNode node : allNodes) {
            if (!node.isDisplayed() || node.isInputWidget()) continue;
            String labelText = node.hasText() ? node.getText() : (node.hasContentDesc() ? node.getContentDesc() : "");
            if (labelText.isEmpty()) continue;
            double sim = Math.max(
                    StringSimilarity.compositeScore(labelText, labelHint),
                    StringSimilarity.tokenOverlap(labelText, labelHint)
            );
            if (sim > bestSim && sim >= MIN_LABEL_SIMILARITY) {
                bestSim = sim;
                bestLabel = node;
            }
        }
        return bestLabel == null ? Optional.empty()
                : findAlignedNode(bestLabel, allNodes, AlignmentMode.AUTO_BEST_PROXIMITY, action, expectedTag, labelHint);
    }

    public Optional<UIElementNode> findAlignedNode(UIElementNode ref, List<UIElementNode> allNodes, AlignmentMode mode) {
        return findAlignedNode(ref, allNodes, mode, null, null, null);
    }

    public Optional<UIElementNode> findAlignedNode(UIElementNode ref, List<UIElementNode> allNodes, AlignmentMode mode,
                                                   String action, String expectedTag, String labelHint) {
        if (ref == null || ref.getBounds() == null || allNodes == null) return Optional.empty();
        String expLower = expectedTag != null ? expectedTag.toLowerCase(Locale.ROOT) : "";
        boolean isInputIntent = ("SET_TEXT".equalsIgnoreCase(action) || "INPUT".equalsIgnoreCase(action)
                || "CLEAR_TEXT".equalsIgnoreCase(action) || "FILL".equalsIgnoreCase(action))
                || expLower.contains("edittext") || expLower.contains("textfield") || expLower.contains("input");
        boolean isCheckboxIntent = expLower.contains("checkbox") || expLower.contains("radio")
                || expLower.contains("switch") || isCheckboxSemanticHint(labelHint);

        return Optional.ofNullable(SpatialCandidateScorer.selectBestAlignedCandidate(
                ref, allNodes, mode, isInputIntent, isCheckboxIntent));
    }

    public static boolean isCheckboxSemanticHint(String hint) {
        if (hint == null || hint.isEmpty()) return false;
        String h = hint.toLowerCase(Locale.ROOT);
        return h.contains("setuju") || h.contains("syarat") || h.contains("ketentuan")
                || h.contains("snk") || h.contains("s&k") || h.contains("centang")
                || h.contains("checkbox") || h.contains("check box") || h.contains("agree")
                || h.contains("terms") || h.contains("ingat saya") || h.contains("remember");
    }

    public List<SpatialPair> extractAllPairs(List<UIElementNode> allNodes) {
        List<SpatialPair> pairs = new ArrayList<>();
        if (allNodes == null || allNodes.isEmpty()) return pairs;

        List<UIElementNode> candidateNodes = new ArrayList<>();
        List<UIElementNode> labelNodes = new ArrayList<>();
        for (UIElementNode node : allNodes) {
            if (!node.isDisplayed() || node.getBounds() == null || node.getBounds().width <= 0 || node.getBounds().height <= 0) continue;
            if (node.getTag() != null && node.getTag().toLowerCase(Locale.ROOT).contains("button")) continue;
            candidateNodes.add(node);
            if (node.hasText() && !node.getText().trim().isEmpty()) labelNodes.add(node);
        }

        Set<UIElementNode> claimedValues = new HashSet<>();
        for (UIElementNode label : labelNodes) {
            if (claimedValues.contains(label)) continue;
            List<UIElementNode> unclaimedCandidates = new ArrayList<>(candidateNodes.size());
            for (UIElementNode cand : candidateNodes) {
                if (cand == label || !claimedValues.contains(cand)) {
                    unclaimedCandidates.add(cand);
                }
            }
            Optional<UIElementNode> matchedVal = findAlignedNode(label, unclaimedCandidates, AlignmentMode.LEFT_TO_RIGHT);
            AlignmentMode usedMode = AlignmentMode.LEFT_TO_RIGHT;

            if (!matchedVal.isPresent()) {
                Rectangle rBounds = label.getBounds();
                int rX1 = rBounds.x, rY2 = rBounds.y + rBounds.height;
                for (UIElementNode cand : unclaimedCandidates) {
                    if (cand == label || !cand.hasText()) continue;
                    Rectangle cBounds = cand.getBounds();
                    int vDist = cBounds.y - rY2;
                    if (vDist >= 0 && vDist <= 40 && Math.abs(cBounds.x - rX1) <= 40) {
                        matchedVal = Optional.of(cand);
                        usedMode = AlignmentMode.TOP_TO_BOTTOM;
                        break;
                    }
                }
            }

            if (matchedVal.isPresent() && claimedValues.add(matchedVal.get())) {
                pairs.add(new SpatialPair(label, matchedVal.get(), usedMode, 0.90));
            }
        }
        return pairs;
    }
}
