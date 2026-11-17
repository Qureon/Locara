package com.qureon.locara.core.spatial;

import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.core.spatial.SpatialAlignmentEngine.AlignmentMode;

import java.awt.Rectangle;
import java.util.List;

/**
 * Evaluates directional 2D alignment scores, widget-specificity bonuses, and DOM ancestor/descendant helpers
 * on behalf of {@link SpatialAlignmentEngine}.
 */
final class SpatialCandidateScorer {

    private static final double SCORE_TIE_EPSILON = 0.001;

    private SpatialCandidateScorer() {}

    static UIElementNode selectBestAlignedCandidate(UIElementNode ref, List<UIElementNode> allNodes, AlignmentMode mode,
                                                    boolean isInputIntent, boolean isCheckboxIntent) {
        Rectangle rBounds = ref.getBounds();
        int rX1 = rBounds.x, rY1 = rBounds.y;
        int rX2 = rBounds.x + rBounds.width, rY2 = rBounds.y + rBounds.height;

        UIElementNode bestCandidate = null;
        double bestScore = 0.0;

        for (UIElementNode cand : allNodes) {
            if (cand == ref || !cand.isDisplayed() || cand.getBounds() == null || isAncestorOf(cand, ref)) continue;
            Rectangle cBounds = cand.getBounds();
            if (cBounds.width <= 0 || cBounds.height <= 0) continue;

            boolean isInteractive = cand.isClickable() || cand.isCheckable() || cand.isInputWidget()
                    || cand.isCheckboxOrToggleWidget() || cand.isCompactCheckboxGeometry() || cand.hasText();
            if (!isInteractive) continue;

            double specificityBonus = computeSpecificityBonus(cand, isInputIntent, isCheckboxIntent);
            double candScore = evaluateDirections(ref, rBounds, rX1, rY1, rX2, rY2, cand, cBounds,
                    mode, isCheckboxIntent, specificityBonus);
            if (candScore > 0.0 && isBetterCandidate(candScore, bestScore, cand, bestCandidate)) {
                bestScore = candScore;
                bestCandidate = cand;
            }
        }

        if (bestCandidate != null && isInputIntent && !bestCandidate.isInputWidget()) {
            UIElementNode childInput = findDescendantInput(bestCandidate);
            if (childInput != null) return childInput;
        }
        return bestCandidate;
    }

    private static double computeSpecificityBonus(UIElementNode cand, boolean isInputIntent, boolean isCheckboxIntent) {
        if (isInputIntent) {
            if (cand.isInputWidget()) return 0.35;
            if (cand.getTag().toLowerCase().contains("viewgroup")) return -0.10;
        } else if (isCheckboxIntent) {
            if (cand.isCheckboxOrToggleWidget()) return 0.35;
            if (cand.isCompactCheckboxGeometry()) return 0.30;
        }
        return 0.0;
    }

    private static double evaluateDirections(UIElementNode ref, Rectangle rBounds, int rX1, int rY1, int rX2, int rY2,
                                             UIElementNode cand, Rectangle cBounds, AlignmentMode mode,
                                             boolean isCheckboxIntent, double specificityBonus) {
        int cX1 = cBounds.x, cY1 = cBounds.y;
        int cX2 = cBounds.x + cBounds.width, cY2 = cBounds.y + cBounds.height;
        double maxScore = -1.0;

        if ((mode == AlignmentMode.LEFT_TO_RIGHT || mode == AlignmentMode.AUTO_BEST_PROXIMITY) && cX1 >= rX1 && cX2 > rX2) {
            double vOverlap = (double) Math.max(0, Math.min(rY2, cY2) - Math.max(rY1, cY1)) / Math.max(1, Math.min(rBounds.height, cBounds.height));
            if (vOverlap >= 0.40) {
                double distScore = 1.0 / (1.0 + (Math.max(0, cX1 - rX2) / 100.0));
                maxScore = Math.max(maxScore, (vOverlap * 0.6) + (distScore * 0.4) + specificityBonus);
            }
        }

        if (mode == AlignmentMode.RIGHT_TO_LEFT
                || (mode == AlignmentMode.AUTO_BEST_PROXIMITY && (isCheckboxIntent || cand.isCheckboxOrToggleWidget() || cand.isCompactCheckboxGeometry()))) {
            if (cX2 <= rX1 + 30 && cX1 < rX1) {
                double vOverlap = (double) Math.max(0, Math.min(rY2, cY2) - Math.max(rY1, cY1)) / Math.max(1, Math.min(rBounds.height, cBounds.height));
                boolean centerAligned = Math.abs(cand.getCenterY() - ref.getCenterY()) <= 60;
                if (vOverlap >= 0.30 || centerAligned) {
                    int hDist = Math.max(0, rX1 - cX2);
                    if (hDist <= 240) {
                        double vScore = Math.max(vOverlap, centerAligned ? 0.85 : 0.0);
                        double distScore = 1.0 / (1.0 + (hDist / 80.0));
                        double checkBonus = (cand.isCheckboxOrToggleWidget() || cand.isCompactCheckboxGeometry()) ? 0.25 : 0.0;
                        maxScore = Math.max(maxScore, (vScore * 0.55) + (distScore * 0.45) + specificityBonus + checkBonus);
                    }
                }
            }
        }

        if ((mode == AlignmentMode.TOP_TO_BOTTOM || mode == AlignmentMode.AUTO_BEST_PROXIMITY) && cY1 >= rY1 && cY2 > rY2) {
            double hOverlap = (double) Math.max(0, Math.min(rX2, cX2) - Math.max(rX1, cX1)) / Math.max(1, Math.min(rBounds.width, cBounds.width));
            if (hOverlap >= 0.30) {
                int vDist = Math.max(0, cY1 - rY2);
                if (vDist <= 350) {
                    double distScore = 1.0 / (1.0 + (vDist / 80.0));
                    maxScore = Math.max(maxScore, (hOverlap * 0.5) + (distScore * 0.5) + specificityBonus);
                }
            }
        }
        return maxScore;
    }

    private static boolean isBetterCandidate(double newScore, double currentBestScore, UIElementNode cand, UIElementNode currentBest) {
        if (currentBest == null || newScore > currentBestScore + SCORE_TIE_EPSILON) return true;
        if (Math.abs(newScore - currentBestScore) <= SCORE_TIE_EPSILON) {
            if (cand.isInputWidget() && !currentBest.isInputWidget()) return true;
            if (cand.isCheckboxOrToggleWidget() && !currentBest.isCheckboxOrToggleWidget()) return true;
            return cand.getDepth() > currentBest.getDepth();
        }
        return false;
    }

    private static boolean isAncestorOf(UIElementNode potentialAncestor, UIElementNode node) {
        for (UIElementNode curr = node != null ? node.getParent() : null; curr != null; curr = curr.getParent()) {
            if (curr == potentialAncestor) return true;
        }
        return false;
    }

    private static UIElementNode findDescendantInput(UIElementNode container) {
        if (container == null) return null;
        for (UIElementNode child : container.getChildren()) {
            if (child.isDisplayed() && child.isInputWidget()) return child;
            UIElementNode nested = findDescendantInput(child);
            if (nested != null) return nested;
        }
        return null;
    }
}
