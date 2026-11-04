package com.qureon.locara.core;

import com.qureon.locara.core.generator.KatalonRsSynthesizer;
import com.qureon.locara.core.ledger.HealingAuditLedger;
import com.qureon.locara.core.matcher.HeuristicMatcher;
import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.core.parser.PageSourceParser;

import java.io.File;
import java.util.List;

/**
 * Master facade and orchestrator for the Locara by Qureon autonomous engine.
 */
public class LocaraEngine {
    private final HealConfig config;
    private final PageSourceParser parser;
    private final HeuristicMatcher matcher;
    private final KatalonRsSynthesizer synthesizer;
    private final HealingAuditLedger ledger;
    private final com.qureon.locara.core.ledger.VisualEvidenceAnnotator visualAnnotator;
    private final com.qureon.locara.core.framework.FrameworkDetector frameworkDetector;
    private final com.qureon.locara.core.spatial.SpatialAlignmentEngine spatialEngine;
    private final com.qureon.locara.core.generator.DynamicXPathGenerator xpathGenerator;

    public LocaraEngine() {
        this(new HealConfig());
    }

    public LocaraEngine(HealConfig config) {
        this.config = config != null ? config : new HealConfig();
        this.parser = new PageSourceParser();
        this.matcher = new HeuristicMatcher();
        this.synthesizer = new KatalonRsSynthesizer();
        this.ledger = new HealingAuditLedger();
        this.visualAnnotator = new com.qureon.locara.core.ledger.VisualEvidenceAnnotator();
        this.frameworkDetector = new com.qureon.locara.core.framework.FrameworkDetector();
        this.spatialEngine = new com.qureon.locara.core.spatial.SpatialAlignmentEngine();
        this.xpathGenerator = new com.qureon.locara.core.generator.DynamicXPathGenerator();
    }

    public HealConfig getConfig() {
        return config;
    }

    public com.qureon.locara.core.framework.FrameworkDetector getFrameworkDetector() {
        return frameworkDetector;
    }

    /**
     * Executes the full autonomous healing pipeline:
     * 1. Parses raw page source XML.
     * 2. Detects underlying mobile framework (React Native, Compose, Flutter, Native Android).
     * 3. Evaluates candidates using multi-vector heuristic similarity with adaptive framework weights.
     * 4. Synthesizes official Katalon .rs Object Repository file.
     * 5. Logs to the immutable audit ledger.
     */
    public HealResult heal(String xmlPageSource, HealContext context) {
        if (context == null) {
            context = new HealContext.Builder().build();
        }
        if (xmlPageSource == null || xmlPageSource.trim().isEmpty()) {
            ledger.recordEvent(context, null, null, "EMPTY_PAGE_SOURCE", config);
            return new HealResult(false, null, null, "XML Page Source is empty or null");
        }

        List<UIElementNode> nodes;
        try {
            nodes = parser.parse(xmlPageSource);
        } catch (Exception e) {
            ledger.recordEvent(context, null, null, "XML_PARSE_ERROR: " + e.getMessage(), config);
            return new HealResult(false, null, null, "Failed to parse XML: " + e.getMessage());
        }

        if (nodes.isEmpty()) {
            ledger.recordEvent(context, null, null, "NO_NODES_FOUND", config);
            return new HealResult(false, null, null, "Zero UI elements found in page source hierarchy");
        }

        // Framework Fingerprinting Detection / Profile Enforcement
        com.qureon.locara.core.framework.FrameworkFingerprint fingerprint = resolveFingerprint(nodes, xmlPageSource);

        List<HealCandidate> candidates = matcher.findCandidates(nodes, context, config, fingerprint);
        String cleanedHint = HeuristicMatcher.cleanSemanticHint(context.getTargetHint());

        // Label-to-Control Pivot: If top candidate for TAP/CHECK is a non-clickable TextView (with no clickable parent),
        // check if it is actually the label for an adjacent interactive CheckBox, compact clickable ViewGroup, or EditText.
        if (!candidates.isEmpty()) {
            UIElementNode topNode = candidates.get(0).getNode();
            String expTag = context.getExpectedTag() != null ? context.getExpectedTag().toLowerCase() : "";
            boolean isTextViewLabel = topNode != null &&
                    (topNode.getTag().toLowerCase().contains("textview") || topNode.getClassName().toLowerCase().contains("textview")) &&
                    !topNode.isClickable() && !hasClickableAncestor(topNode);
            boolean expectsInteractiveControl = expTag.contains("checkbox") || expTag.contains("viewgroup") ||
                    expTag.contains("edittext") || com.qureon.locara.core.spatial.SpatialAlignmentEngine.isCheckboxSemanticHint(cleanedHint);

            if (isTextViewLabel && expectsInteractiveControl) {
                java.util.Optional<UIElementNode> pairedControl = spatialEngine.findAlignedNode(
                        topNode, nodes, com.qureon.locara.core.spatial.SpatialAlignmentEngine.AlignmentMode.AUTO_BEST_PROXIMITY,
                        context.getAction(), context.getExpectedTag(), cleanedHint);
                if (pairedControl.isPresent()) {
                    UIElementNode ctrl = pairedControl.get();
                    if (ctrl.isClickable() || ctrl.isCheckable() || ctrl.isInputWidget() || ctrl.isCheckboxOrToggleWidget()) {
                        String ctrlXPath = xpathGenerator.generateOptimalXPath(ctrl, nodes, context.getAction());
                        HealCandidate pivoted = new HealCandidate(ctrl, ctrlXPath,
                                Math.max(0.86, candidates.get(0).getConfidenceScore()), "SPATIAL_LABEL_TO_CONTROL_PIVOT");
                        candidates = java.util.Collections.singletonList(pivoted);
                    }
                }
            }
        }

        if (candidates.isEmpty() && 0.85 >= config.getMinConfidenceThreshold()) {
            // Spatial Proximity Fallback: Infer target input/checkbox/value element based on visual 2D layout alignment
            java.util.Optional<UIElementNode> spatialNode = spatialEngine.findTargetByLabel(
                    cleanedHint, nodes, context.getAction(), context.getExpectedTag());
            if (spatialNode.isPresent()) {
                UIElementNode sNode = spatialNode.get();
                if (!com.qureon.locara.core.guard.DangerKeywordGuard.isPolarityViolation(cleanedHint, sNode)) {
                    String sXPath = xpathGenerator.generateOptimalXPath(sNode, nodes, context.getAction());
                    HealCandidate sCand = new HealCandidate(sNode, sXPath, 0.85, "SPATIAL_ALIGNMENT_PROXIMITY");
                    candidates = java.util.Collections.singletonList(sCand);
                }
            }

            // Single-Widget Screen Fallback: Resolve sole anonymous EditText or CheckBox when screen has exactly 1 instance
            if (candidates.isEmpty()) {
                java.util.Optional<HealCandidate> singleCand = resolveSingleAnonymousWidget(nodes, context, cleanedHint);
                if (singleCand.isPresent()) {
                    candidates = java.util.Collections.singletonList(singleCand.get());
                }
            }
        }

        if (candidates.isEmpty()) {
            ledger.recordEvent(context, null, null, "THRESHOLD_REJECTED", config, fingerprint);
            return new HealResult(false, null, null, null, fingerprint,
                    String.format("No element matched hint '%s' with confidence >= %.0f%% (Framework: %s)",
                            context.getTargetHint(), config.getMinConfidenceThreshold() * 100.0, fingerprint.getFramework().getDisplayName()));
        }

        HealCandidate winningCandidate = candidates.get(0);
        File generatedRs = null;
        File annotatedScreenshot = null;

        if (context.getRawScreenshot() != null && context.getRawScreenshot().exists()) {
            annotatedScreenshot = visualAnnotator.annotateScreenshot(context.getRawScreenshot(), winningCandidate, config);
        }

        if (config.isAutoGenerateRs()) {
            try {
                generatedRs = synthesizer.synthesizeRsFile(winningCandidate, context, config);
            } catch (Exception e) {
                // Log failure to write file but still return candidate so in-memory action can proceed
                ledger.recordEvent(context, winningCandidate, null, "RS_WRITE_ERROR: " + e.getMessage(), config, fingerprint);
                return new HealResult(true, winningCandidate, null, annotatedScreenshot, fingerprint,
                        "Healed in-memory, but failed to write .rs file: " + e.getMessage());
            }
        }

        ledger.recordEvent(context, winningCandidate, generatedRs, "SUCCESS", config, fingerprint);

        return new HealResult(true, winningCandidate, generatedRs, annotatedScreenshot, fingerprint,
                String.format("Successfully healed with confidence %.1f%% (%s) [Framework: %s]",
                        winningCandidate.getConfidenceScore() * 100.0, winningCandidate.getHealReason(),
                        fingerprint.getFramework().getDisplayName()));
    }

    /**
     * Resets any in-flight temporary state or caches.
     * Essential for Katalon 11.6.0+ pause-and-resume workflows where application states
     * or screen contexts may change across deliberate test suite pauses.
     */
    public void resetSessionContext() {
        // Clear in-memory caches or dynamic context
    }

    /**
     * Inspects the hierarchy and returns all ranked candidate matches without mutating state or writing files.
     */
    public List<HealCandidate> inspectOnly(String xmlPageSource, HealContext context) throws Exception {
        if (context == null) {
            context = new HealContext.Builder().build();
        }
        List<UIElementNode> nodes = parser.parse(xmlPageSource);
        com.qureon.locara.core.framework.FrameworkFingerprint fingerprint = resolveFingerprint(nodes, xmlPageSource);
        return matcher.findCandidates(nodes, context, config, fingerprint);
    }

    private com.qureon.locara.core.framework.FrameworkFingerprint resolveFingerprint(List<UIElementNode> nodes, String xmlPageSource) {
        com.qureon.locara.core.model.AppTarget target = config.getAppTarget();
        if (target == com.qureon.locara.core.model.AppTarget.REVAMP) {
            return new com.qureon.locara.core.framework.FrameworkFingerprint(
                    com.qureon.locara.core.framework.AppFramework.REACT_NATIVE, 1.0, "Enforced Profile: Revamp (React Native)", 0.50);
        } else if (target == com.qureon.locara.core.model.AppTarget.KMP) {
            return new com.qureon.locara.core.framework.FrameworkFingerprint(
                    com.qureon.locara.core.framework.AppFramework.KMP_COMPOSE, 1.0, "Enforced Profile: KMP (Compose Multiplatform)", 0.0);
        } else if (target == com.qureon.locara.core.model.AppTarget.UMUM) {
            return new com.qureon.locara.core.framework.FrameworkFingerprint(
                    com.qureon.locara.core.framework.AppFramework.UNIVERSAL, 1.0, "Enforced Profile: Umum (Universal App-Agnostic)", 0.0);
        } else if (target == com.qureon.locara.core.model.AppTarget.WEB) {
            return new com.qureon.locara.core.framework.FrameworkFingerprint(
                    com.qureon.locara.core.framework.AppFramework.PLAYWRIGHT_WEB, 1.0, "Enforced Profile: Playwright Modern Web (HTML5/ARIA)", 0.0);
        }
        return frameworkDetector.detect(nodes, xmlPageSource);
    }

    private java.util.Optional<HealCandidate> resolveSingleAnonymousWidget(List<UIElementNode> nodes, HealContext context, String cleanedHint) {
        String action = context.getAction() != null ? context.getAction().toUpperCase() : "";
        String expTag = context.getExpectedTag() != null ? context.getExpectedTag().toLowerCase() : "";

        boolean isInputRequest = "SET_TEXT".equals(action) || "INPUT".equals(action) || "CLEAR_TEXT".equals(action) ||
                "FILL".equals(action) || expTag.contains("edittext") || expTag.contains("textfield") || expTag.contains("input");

        if (isInputRequest) {
            List<UIElementNode> inputNodes = new java.util.ArrayList<>();
            for (UIElementNode n : nodes) {
                if (n.isDisplayed() && n.isEnabled() && n.isInputWidget()) {
                    inputNodes.add(n);
                }
            }
            if (inputNodes.size() == 1) {
                UIElementNode soleInput = inputNodes.get(0);
                String xp = xpathGenerator.generateOptimalXPath(soleInput, nodes, context.getAction());
                return java.util.Optional.of(new HealCandidate(soleInput, xp, 0.85, "SINGLE_INPUT_WIDGET_ON_SCREEN"));
            }
        }

        boolean isCheckboxRequest = expTag.contains("checkbox") || expTag.contains("radio") || expTag.contains("switch") ||
                com.qureon.locara.core.spatial.SpatialAlignmentEngine.isCheckboxSemanticHint(cleanedHint);

        if (isCheckboxRequest && ("TAP".equals(action) || "CLICK".equals(action) || "CHECK".equals(action) || action.isEmpty())) {
            List<UIElementNode> checkNodes = new java.util.ArrayList<>();
            for (UIElementNode n : nodes) {
                if (n.isDisplayed() && n.isEnabled() && n.isCheckboxOrToggleWidget()) {
                    checkNodes.add(n);
                }
            }
            if (checkNodes.size() == 1) {
                UIElementNode soleCheck = checkNodes.get(0);
                String xp = xpathGenerator.generateOptimalXPath(soleCheck, nodes, context.getAction());
                return java.util.Optional.of(new HealCandidate(soleCheck, xp, 0.85, "SINGLE_CHECKBOX_ON_SCREEN"));
            } else if (checkNodes.isEmpty()) {
                List<UIElementNode> compactBoxes = new java.util.ArrayList<>();
                for (UIElementNode n : nodes) {
                    if (n.isDisplayed() && n.isEnabled() && n.isCompactCheckboxGeometry() && !n.hasText()) {
                        compactBoxes.add(n);
                    }
                }
                if (compactBoxes.size() == 1) {
                    UIElementNode soleBox = compactBoxes.get(0);
                    String xp = xpathGenerator.generateOptimalXPath(soleBox, nodes, context.getAction());
                    return java.util.Optional.of(new HealCandidate(soleBox, xp, 0.85, "SINGLE_CUSTOM_CHECKBOX_ON_SCREEN"));
                }
            }
        }

        return java.util.Optional.empty();
    }

    private boolean hasClickableAncestor(UIElementNode node) {
        if (node == null) return false;
        UIElementNode curr = node.getParent();
        int levels = 0;
        while (curr != null && levels < 4) {
            if (curr.isClickable()) return true;
            curr = curr.getParent();
            levels++;
        }
        return false;
    }
}
