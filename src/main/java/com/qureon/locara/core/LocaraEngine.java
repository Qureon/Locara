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
        com.qureon.locara.core.framework.FrameworkFingerprint fingerprint;
        com.qureon.locara.core.model.AppTarget target = config.getAppTarget();
        if (target == com.qureon.locara.core.model.AppTarget.REVAMP) {
            fingerprint = new com.qureon.locara.core.framework.FrameworkFingerprint(
                    com.qureon.locara.core.framework.AppFramework.REACT_NATIVE, 1.0, "Enforced Profile: Revamp (React Native)", 0.50);
        } else if (target == com.qureon.locara.core.model.AppTarget.KMP) {
            fingerprint = new com.qureon.locara.core.framework.FrameworkFingerprint(
                    com.qureon.locara.core.framework.AppFramework.KMP_COMPOSE, 1.0, "Enforced Profile: KMP (Compose Multiplatform)", 0.0);
        } else if (target == com.qureon.locara.core.model.AppTarget.UMUM) {
            fingerprint = new com.qureon.locara.core.framework.FrameworkFingerprint(
                    com.qureon.locara.core.framework.AppFramework.UNIVERSAL, 1.0, "Enforced Profile: Umum (Universal App-Agnostic)", 0.0);
        } else {
            fingerprint = frameworkDetector.detect(nodes, xmlPageSource);
        }

        List<HealCandidate> candidates = matcher.findCandidates(nodes, context, config, fingerprint);
        if (candidates.isEmpty()) {
            // Spatial Proximity Fallback: Infer target input/value element based on visual 2D layout alignment
            java.util.Optional<UIElementNode> spatialNode = spatialEngine.findTargetByLabel(context.getTargetHint(), nodes);
            if (spatialNode.isPresent()) {
                UIElementNode sNode = spatialNode.get();
                String sXPath = xpathGenerator.generateOptimalXPath(sNode, nodes, context.getAction());
                HealCandidate sCand = new HealCandidate(sNode, sXPath, 0.85, "SPATIAL_ALIGNMENT_PROXIMITY");
                candidates = java.util.Collections.singletonList(sCand);
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
     * Inspects the hierarchy and returns all ranked candidate matches without mutating state or writing files.
     */
    public List<HealCandidate> inspectOnly(String xmlPageSource, HealContext context) throws Exception {
        List<UIElementNode> nodes = parser.parse(xmlPageSource);
        return matcher.findCandidates(nodes, context, config);
    }
}
