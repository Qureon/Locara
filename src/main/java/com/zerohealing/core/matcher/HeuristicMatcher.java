package com.zerohealing.core.matcher;

import com.zerohealing.core.generator.DynamicXPathGenerator;
import com.zerohealing.core.model.HealCandidate;
import com.zerohealing.core.model.HealConfig;
import com.zerohealing.core.model.HealContext;
import com.zerohealing.core.model.UIElementNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Core multi-vector heuristic matching engine that evaluates and ranks candidate UI elements.
 */
public class HeuristicMatcher {
    private final DynamicXPathGenerator xpathGenerator;

    public HeuristicMatcher() {
        this.xpathGenerator = new DynamicXPathGenerator();
    }

    public List<HealCandidate> findCandidates(List<UIElementNode> nodes, HealContext context, HealConfig config) {
        return findCandidates(nodes, context, config, null);
    }

    public List<HealCandidate> findCandidates(List<UIElementNode> nodes, HealContext context,
                                              HealConfig config,
                                              com.zerohealing.core.framework.FrameworkFingerprint fingerprint) {
        List<HealCandidate> candidates = new ArrayList<>();
        if (nodes == null || nodes.isEmpty() || context == null) {
            return candidates;
        }

        String rawHint = context.getTargetHint();
        String targetHint = cleanSemanticHint(rawHint);
        String action = context.getAction();

        // Framework-aware weight profile
        com.zerohealing.core.framework.FrameworkTuningProfile profile = null;
        if (config.isAdaptiveFrameworkWeights() && fingerprint != null) {
            profile = com.zerohealing.core.framework.FrameworkTuningProfile.getProfileFor(fingerprint.getFramework());
        }

        for (UIElementNode node : nodes) {
            // Filter 1: Must be displayed and have positive bounds
            if (!node.isDisplayed() || node.getBounds().width <= 0 || node.getBounds().height <= 0) {
                continue;
            }

            // Filter 2: Enterprise Danger Polarity Guard (Never match 'Batal'/'Hapus' when seeking 'Lanjut'/'Bayar')
            if (com.zerohealing.core.guard.DangerKeywordGuard.isPolarityViolation(targetHint, node)) {
                continue;
            }

            // Calculate Vector 1: Text Similarity
            double textScore = 0.0;
            if (node.hasText()) {
                textScore = StringSimilarity.compositeScore(node.getText(), targetHint);
            }

            // Calculate Vector 2: Resource ID Semantic Similarity
            double idScore = 0.0;
            if (node.hasResourceId()) {
                idScore = StringSimilarity.compositeScore(node.getStrippedResourceId(), targetHint);
            }

            // Calculate Vector 3: Content-Description Similarity
            double descScore = 0.0;
            if (node.hasContentDesc()) {
                descScore = StringSimilarity.compositeScore(node.getContentDesc(), targetHint);
            }

            // Calculate Vector 4: Class & Action Compatibility
            double classScore = calculateClassCompatibility(node, action, context.getExpectedTag());

            // Calculate Vector 5: Clickability & Proximity
            double proximityScore = calculateProximityScore(node, action);

            // Dynamic weight selection (Profile-based or config-based)
            double wText = profile != null ? profile.getWeightText() : config.getWeightText();
            double wId = profile != null ? profile.getWeightId() : config.getWeightId();
            double wDesc = profile != null ? profile.getWeightDesc() : config.getWeightDesc();
            double wClass = profile != null ? profile.getWeightClass() : config.getWeightClass();
            double wProx = profile != null ? profile.getWeightProximity() : config.getWeightProximity();

            // Dynamic weight redistribution across available semantic vectors
            boolean hasText = node.hasText();
            boolean hasId = node.hasResourceId();
            boolean hasDesc = node.hasContentDesc();

            if (!hasText && !hasId && hasDesc) {
                // Element only has content-desc (common in React Native / M-Smile icons & buttons)
                wDesc += wText + wId;
                wText = 0.0;
                wId = 0.0;
            } else if (!hasText && hasId && !hasDesc) {
                // Element only has resource-id
                wId += wText + wDesc;
                wText = 0.0;
                wDesc = 0.0;
            } else if (hasText && !hasId && !hasDesc) {
                // Element only has text
                wText += wId + wDesc;
                wId = 0.0;
                wDesc = 0.0;
            } else if (!hasText && hasId && hasDesc) {
                // Has resource-id and content-desc, no text
                wId += wText * 0.5;
                wDesc += wText * 0.5;
                wText = 0.0;
            } else if (hasText && !hasId && hasDesc) {
                // Has text and content-desc, no resource-id
                wText += wId * 0.5;
                wDesc += wId * 0.5;
                wId = 0.0;
            } else if (hasText && hasId && !hasDesc) {
                // Has text and resource-id, no content-desc
                wText += wDesc * 0.5;
                wId += wDesc * 0.5;
                wDesc = 0.0;
            }

            // Calculate Weighted Total Score
            double totalScore = (textScore * wText) +
                    (idScore * wId) +
                    (descScore * wDesc) +
                    (classScore * wClass) +
                    (proximityScore * wProx);

            // Normalized Cap
            totalScore = Math.min(1.0, Math.max(0.0, totalScore));

            if (totalScore >= config.getMinConfidenceThreshold()) {
                String optimalXPath = xpathGenerator.generateOptimalXPath(node, nodes, action);
                String reason = buildHealReason(textScore, idScore, descScore, classScore);

                candidates.add(new HealCandidate(
                        node,
                        totalScore,
                        textScore,
                        idScore,
                        descScore,
                        classScore,
                        proximityScore,
                        optimalXPath,
                        reason
                ));
            }
        }

        Collections.sort(candidates);
        return candidates;
    }

    private double calculateClassCompatibility(UIElementNode node, String action, String expectedTag) {
        double score = 0.5; // neutral baseline
        String tag = node.getTag().toLowerCase();
        String className = node.getClassName().toLowerCase();

        if (expectedTag != null && !expectedTag.isEmpty() && (tag.contains(expectedTag.toLowerCase()) || className.contains(expectedTag.toLowerCase()))) {
            score = 1.0;
        } else if ("TAP".equalsIgnoreCase(action) || "CLICK".equalsIgnoreCase(action)) {
            if (tag.contains("button") || className.contains("button")) {
                score = 1.0;
            } else if (tag.contains("imageview") || className.contains("imageview")) {
                score = node.isClickable() ? 0.90 : 0.60;
            } else if (tag.contains("textview") || className.contains("textview")) {
                // If TextView is clickable, 0.85. If not clickable but wrapped by a clickable container (React Native CButton / TouchableOpacity), 0.85.
                boolean hasClickableParent = hasClickableAncestor(node);
                score = (node.isClickable() || hasClickableParent) ? 0.85 : 0.60;
            } else if (tag.contains("viewgroup") || className.contains("viewgroup") || tag.contains("view") || className.contains("view")) {
                score = node.isClickable() ? 0.90 : 0.75;
            } else if (node.isClickable()) {
                score = 0.80;
            }
        } else if ("SET_TEXT".equalsIgnoreCase(action) || "INPUT".equalsIgnoreCase(action) || "CLEAR_TEXT".equalsIgnoreCase(action)) {
            if (tag.contains("edittext") || className.contains("edittext") || tag.contains("textfield")) {
                score = 1.0;
            } else if (tag.contains("viewgroup") || className.contains("viewgroup")) {
                score = 0.70;
            }
        } else if ("GET_TEXT".equalsIgnoreCase(action) || "READ".equalsIgnoreCase(action)) {
            if (tag.contains("textview") || className.contains("textview")) {
                score = 1.0;
            } else if (tag.contains("edittext") || className.contains("edittext")) {
                score = 0.90;
            } else if (node.hasText() || node.hasContentDesc()) {
                score = 0.85;
            }
        }
        return score;
    }

    private double calculateProximityScore(UIElementNode node, String action) {
        double score = 0.5;
        if (node.isClickable() || (("TAP".equalsIgnoreCase(action) || "CLICK".equalsIgnoreCase(action)) && hasClickableAncestor(node))) {
            score += 0.3;
        }
        if (node.isEnabled()) {
            score += 0.2;
        }
        return Math.min(1.0, score);
    }

    private boolean hasClickableAncestor(UIElementNode node) {
        if (node == null) return false;
        UIElementNode current = node.getParent();
        int levels = 0;
        while (current != null && levels < 4) {
            if (current.isClickable()) return true;
            current = current.getParent();
            levels++;
        }
        return false;
    }

    private String buildHealReason(double textScore, double idScore, double descScore, double classScore) {
        if (textScore >= 0.85) {
            return String.format("High text match (%.0f%%)", textScore * 100);
        } else if (idScore >= 0.85) {
            return String.format("High resource-id semantic match (%.0f%%)", idScore * 100);
        } else if (descScore >= 0.85) {
            return String.format("High content-desc match (%.0f%%)", descScore * 100);
        } else {
            return "Multi-vector composite match";
        }
    }

    /**
     * Sanitizes full Katalon Object Repository paths, removing directory segments,
     * single-tier ('01. ') and 2-tier ('05-2. ') numeric indices, widget prefixes,
     * and semantic qualifiers ('Label', 'Value', 'Tombol').
     */
    public static String cleanSemanticHint(String rawHint) {
        if (rawHint == null || rawHint.trim().isEmpty()) return "";
        String h = rawHint.replace("\\", "/");
        if (h.contains("/")) {
            h = h.substring(h.lastIndexOf('/') + 1);
        }
        if (h.endsWith(".rs")) {
            h = h.substring(0, h.length() - 3);
        }
        // Strip single-tier ('01. ', '1. ') and 2-tier ('05-1. ', '05-2. ', '1-2. ') numeric prefixes
        h = h.replaceAll("^[0-9]+(?:-[0-9]+)?\\.\\s*", "");
        // Strip common Katalon/Android widget prefixes with optional 'Tombol'
        h = h.replaceAll("(?i)^(ViewGroup|GroupView|TextView|Button|EditText|ImageView|ImageButton|ProgressBar|Banner|CheckBox|RadioButton|View)\\s*-\\s*(?:Tombol\\s*)?", "");
        // Strip leading qualifiers like 'Label ', 'Value ', 'Tombol ', 'Box '
        h = h.replaceAll("(?i)^(Label|Value|Tombol|Box)\\s+", "");
        return h.trim();
    }
}
