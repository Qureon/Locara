package com.qureon.locara.core;

import com.qureon.locara.core.framework.AppFramework;
import com.qureon.locara.core.framework.FrameworkDetector;
import com.qureon.locara.core.framework.FrameworkFingerprint;
import com.qureon.locara.core.generator.DynamicXPathGenerator;
import com.qureon.locara.core.generator.KatalonRsSynthesizer;
import com.qureon.locara.core.guard.DangerKeywordGuard;
import com.qureon.locara.core.ledger.HealingAuditLedger;
import com.qureon.locara.core.ledger.VisualEvidenceAnnotator;
import com.qureon.locara.core.matcher.HeuristicMatcher;
import com.qureon.locara.core.model.AppTarget;
import com.qureon.locara.core.model.HealCandidate;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.core.parser.PageSourceParser;
import com.qureon.locara.core.spatial.SpatialAlignmentEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Master facade and orchestrator for the Locara by Qureon autonomous engine.
 */
public class LocaraEngine {
    private final HealConfig config;
    private final PageSourceParser parser;
    private final HeuristicMatcher matcher;
    private final KatalonRsSynthesizer synthesizer;
    private final HealingAuditLedger ledger;
    private final VisualEvidenceAnnotator visualAnnotator;
    private final FrameworkDetector frameworkDetector;
    private final SpatialAlignmentEngine spatialEngine;
    private final DynamicXPathGenerator xpathGenerator;

    public LocaraEngine() {
        this(new HealConfig());
    }

    public LocaraEngine(HealConfig config) {
        this.config = config != null ? config : new HealConfig();
        this.parser = new PageSourceParser();
        this.matcher = new HeuristicMatcher();
        this.synthesizer = new KatalonRsSynthesizer();
        this.ledger = new HealingAuditLedger();
        this.visualAnnotator = new VisualEvidenceAnnotator();
        this.frameworkDetector = new FrameworkDetector();
        this.spatialEngine = new SpatialAlignmentEngine();
        this.xpathGenerator = new DynamicXPathGenerator();
    }

    public HealConfig getConfig() {
        return config;
    }

    public FrameworkDetector getFrameworkDetector() {
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
        return heal(null, xmlPageSource, context);
    }

    /**
     * Parses raw XML or HTML page source once so downstream healing/overlay/scroll stages
     * can reuse the resulting UI hierarchy without redundant DOM parses.
     */
    public List<UIElementNode> parsePageSource(String xmlPageSource) throws Exception {
        return parser.parse(xmlPageSource);
    }

    /**
     * Executes autonomous healing using pre-parsed hierarchy nodes when available.
     */
    public HealResult heal(List<UIElementNode> parsedNodes, String rawPageSource, HealContext context) {
        if (context == null) {
            context = new HealContext.Builder().build();
        }
        List<UIElementNode> nodes = parsedNodes;
        if (nodes == null) {
            if (rawPageSource == null || rawPageSource.trim().isEmpty()) {
                ledger.recordEvent(context, null, null, "EMPTY_PAGE_SOURCE", config);
                return new HealResult(false, null, null, "XML Page Source is empty or null");
            }
            try {
                nodes = parsePageSource(rawPageSource);
            } catch (Exception e) {
                ledger.recordEvent(context, null, null, "XML_PARSE_ERROR: " + e.getMessage(), config);
                return new HealResult(false, null, null, "Failed to parse XML: " + e.getMessage());
            }
        }

        if (nodes.isEmpty()) {
            ledger.recordEvent(context, null, null, "NO_NODES_FOUND", config);
            return new HealResult(false, null, null, "Zero UI elements found in page source hierarchy");
        }

        // Framework Fingerprinting Detection / Profile Enforcement
        FrameworkFingerprint fingerprint = resolveFingerprint(nodes, rawPageSource);

        List<HealCandidate> candidates = matcher.findCandidates(nodes, context, config, fingerprint);
        String cleanedHint = HeuristicMatcher.cleanSemanticHint(context.getTargetHint());

        // Label-to-Control Pivot: If top candidate for TAP/CHECK is a non-clickable TextView (with no clickable parent),
        // check if it is actually the label for an adjacent interactive CheckBox, compact clickable ViewGroup, or EditText.
        if (!candidates.isEmpty()) {
            UIElementNode topNode = candidates.get(0).getNode();
            String expTag = context.getExpectedTag() != null ? context.getExpectedTag().toLowerCase(Locale.ROOT) : "";
            boolean isTextViewLabel = topNode != null &&
                    (topNode.getTag().toLowerCase(Locale.ROOT).contains("textview") || topNode.getClassName().toLowerCase(Locale.ROOT).contains("textview")) &&
                    !topNode.isClickable() && !topNode.hasClickableAncestor(4);
            boolean expectsInteractiveControl = expTag.contains("checkbox") || expTag.contains("viewgroup") ||
                    expTag.contains("edittext") || SpatialAlignmentEngine.isCheckboxSemanticHint(cleanedHint);

            if (isTextViewLabel && expectsInteractiveControl) {
                Optional<UIElementNode> pairedControl = spatialEngine.findAlignedNode(
                        topNode, nodes, SpatialAlignmentEngine.AlignmentMode.AUTO_BEST_PROXIMITY,
                        context.getAction(), context.getExpectedTag(), cleanedHint);
                if (pairedControl.isPresent()) {
                    UIElementNode ctrl = pairedControl.get();
                    boolean interactive = ctrl.isClickable() || ctrl.isCheckable() || ctrl.isInputWidget() || ctrl.isCheckboxOrToggleWidget();
                    if (interactive && !DangerKeywordGuard.isPolarityViolation(cleanedHint, ctrl)) {
                        String ctrlXPath = xpathGenerator.generateOptimalXPath(ctrl, nodes, context.getAction());
                        HealCandidate pivoted = new HealCandidate(ctrl, ctrlXPath,
                                Math.max(0.86, candidates.get(0).getConfidenceScore()), "SPATIAL_LABEL_TO_CONTROL_PIVOT");
                        candidates = Collections.singletonList(pivoted);
                    }
                }
            }
        }

        if (candidates.isEmpty() && 0.85 >= config.getMinConfidenceThreshold()) {
            // Spatial Proximity Fallback: Infer target input/checkbox/value element based on visual 2D layout alignment
            Optional<UIElementNode> spatialNode = spatialEngine.findTargetByLabel(
                    cleanedHint, nodes, context.getAction(), context.getExpectedTag());
            if (spatialNode.isPresent()) {
                UIElementNode sNode = spatialNode.get();
                if (!DangerKeywordGuard.isPolarityViolation(cleanedHint, sNode)) {
                    String sXPath = xpathGenerator.generateOptimalXPath(sNode, nodes, context.getAction());
                    HealCandidate sCand = new HealCandidate(sNode, sXPath, 0.85, "SPATIAL_ALIGNMENT_PROXIMITY");
                    candidates = Collections.singletonList(sCand);
                }
            }

            // Single-Widget Screen Fallback: Resolve sole anonymous EditText or CheckBox when screen has exactly 1 instance
            if (candidates.isEmpty()) {
                Optional<HealCandidate> singleCand = resolveSingleAnonymousWidget(nodes, context, cleanedHint);
                if (singleCand.isPresent()) {
                    candidates = Collections.singletonList(singleCand.get());
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
        config.clearThreadAppTarget();
    }

    /**
     * Inspects the hierarchy and returns all ranked candidate matches without mutating state or writing files.
     */
    public List<HealCandidate> inspectOnly(String xmlPageSource, HealContext context) throws Exception {
        if (context == null) {
            context = new HealContext.Builder().build();
        }
        List<UIElementNode> nodes = parser.parse(xmlPageSource);
        FrameworkFingerprint fingerprint = resolveFingerprint(nodes, xmlPageSource);
        return matcher.findCandidates(nodes, context, config, fingerprint);
    }

    private FrameworkFingerprint resolveFingerprint(List<UIElementNode> nodes, String xmlPageSource) {
        AppTarget target = config.getAppTarget();
        if (target == AppTarget.REVAMP) {
            return new FrameworkFingerprint(AppFramework.REACT_NATIVE, 1.0, "Enforced Profile: Revamp (React Native)", 0.50);
        } else if (target == AppTarget.KMP) {
            return new FrameworkFingerprint(AppFramework.KMP_COMPOSE, 1.0, "Enforced Profile: KMP (Compose Multiplatform)", 0.0);
        } else if (target == AppTarget.UMUM) {
            return new FrameworkFingerprint(AppFramework.UNIVERSAL, 1.0, "Enforced Profile: Umum (Universal App-Agnostic)", 0.0);
        } else if (target == AppTarget.WEB) {
            return new FrameworkFingerprint(AppFramework.PLAYWRIGHT_WEB, 1.0, "Enforced Profile: Playwright Modern Web (HTML5/ARIA)", 0.0);
        }
        return frameworkDetector.detect(nodes, xmlPageSource);
    }

    private Optional<HealCandidate> resolveSingleAnonymousWidget(List<UIElementNode> nodes, HealContext context, String cleanedHint) {
        String action = context.getAction() != null ? context.getAction().toUpperCase(Locale.ROOT) : "";
        String expTag = context.getExpectedTag() != null ? context.getExpectedTag().toLowerCase(Locale.ROOT) : "";

        boolean isInputRequest = "SET_TEXT".equals(action) || "INPUT".equals(action) || "CLEAR_TEXT".equals(action) ||
                "FILL".equals(action) || expTag.contains("edittext") || expTag.contains("textfield") || expTag.contains("input");

        if (isInputRequest) {
            List<UIElementNode> inputNodes = new ArrayList<>();
            for (UIElementNode n : nodes) {
                if (n.isDisplayed() && n.isEnabled() && n.isInputWidget()) {
                    inputNodes.add(n);
                }
            }
            if (inputNodes.size() == 1 && !DangerKeywordGuard.isPolarityViolation(cleanedHint, inputNodes.get(0))) {
                UIElementNode soleInput = inputNodes.get(0);
                return Optional.of(new HealCandidate(soleInput,
                        xpathGenerator.generateOptimalXPath(soleInput, nodes, context.getAction()),
                        0.85, "SINGLE_INPUT_WIDGET_ON_SCREEN"));
            }
        }

        boolean isCheckboxRequest = expTag.contains("checkbox") || expTag.contains("radio") || expTag.contains("switch") ||
                SpatialAlignmentEngine.isCheckboxSemanticHint(cleanedHint);

        if (isCheckboxRequest && ("TAP".equals(action) || "CLICK".equals(action) || "CHECK".equals(action) || action.isEmpty())) {
            List<UIElementNode> checkNodes = new ArrayList<>();
            for (UIElementNode n : nodes) {
                if (n.isDisplayed() && n.isEnabled() && n.isCheckboxOrToggleWidget()) {
                    checkNodes.add(n);
                }
            }
            if (checkNodes.size() == 1 && !DangerKeywordGuard.isPolarityViolation(cleanedHint, checkNodes.get(0))) {
                UIElementNode soleCheck = checkNodes.get(0);
                return Optional.of(new HealCandidate(soleCheck,
                        xpathGenerator.generateOptimalXPath(soleCheck, nodes, context.getAction()),
                        0.85, "SINGLE_CHECKBOX_ON_SCREEN"));
            } else if (checkNodes.isEmpty()) {
                List<UIElementNode> compactBoxes = new ArrayList<>();
                for (UIElementNode n : nodes) {
                    if (n.isDisplayed() && n.isEnabled() && n.isCompactCheckboxGeometry() && !n.hasText()) {
                        compactBoxes.add(n);
                    }
                }
                if (compactBoxes.size() == 1 && !DangerKeywordGuard.isPolarityViolation(cleanedHint, compactBoxes.get(0))) {
                    UIElementNode soleBox = compactBoxes.get(0);
                    return Optional.of(new HealCandidate(soleBox,
                            xpathGenerator.generateOptimalXPath(soleBox, nodes, context.getAction()),
                            0.85, "SINGLE_CUSTOM_CHECKBOX_ON_SCREEN"));
                }
            }
        }

        return Optional.empty();
    }
}
