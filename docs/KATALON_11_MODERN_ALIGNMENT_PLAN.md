# 🚀 LOCARA BY QUREON — KATALON STUDIO 11.6.0 & 11.7.0 MODERN ALIGNMENT & DASHBOARD EVOLUTION PLAN

> **Target Platform**: Katalon Studio 8.x – 11.7.0+ (Universal & Project-Agnostic)  
> **Runtime**: Eclipse Temurin 21.0.12 LTS & Java 17  
> **Status**: Ready for Surgical Execution  
> **Architect**: Kaela Kovalskia (Ela 🌸) for Rio  

---

## 1. Executive Summary & Release Notes Analysis

Analysis of Katalon Studio 11.0.0 through 11.7.0 (with emphasis on 11.6.0 & 11.7.0) highlights key architectural shifts that directly interface with Locara's autonomous self-healing and reconnaissance pipeline:

| Katalon 11 Feature | Katalon Behavior | Locara Impact & Upgrade Path |
| :--- | :--- | :--- |
| **Editable XPath Locators (11.6.0)** | Web Test Objects support an *Object's XPaths* table with named, ordered, editable locator entries. | Upgrade `KatalonRsSynthesizer` to synthesize **Ranked Multi-XPath Collections** (`xpath:attributes`, `xpath:idRelative`, `xpath:position`, `xpath:custom`) in Web & Mobile `.rs` files. |
| **26 Element State Keywords (11.2 - 11.7)** | Introduces 26 `isElement*` (branching, returns boolean) and 26 `assertElement*` (checkpoints, throws error). **Native self-healing explicitly does NOT support these.** | Expand `KeywordIntentionClassifier` to classify all 26 `isElement*` as `CONDITIONAL_PROBE` (non-destructive probe) and all 26 `assertElement*` as `CHECKPOINT` (active heal before checkpoint). |
| **Pause & Resume Test Suite (11.6.0)** | Executions can be paused between test cases in Job Progress and resumed. | Guarantee atomic flush in `HealingAuditLedger` and add session context invalidation in `LocaraEngine` so paused runs do not leak resources or retain stale screen hierarchies. |
| **Intention Groups & Metadata (11.3.0)** | Steps grouped under parent intention with `@expected` and `@data` tags. | Add `intentionGroup` and `executionTags` to `HealContext` and telemetry records for end-to-end traceability. |
| **Dynamic Test Suite Boolean Tags (11.6.0)** | Tag queries with `AND` / `OR` expressions. | Add tag parsing and filtering to telemetry ledger and Mission Control Dashboard. |
| **Temurin 21.0.12 LTS & Eclipse 2026-09** | Bundled JVM updated to Temurin 21.0.12 LTS. | Verify bytecode compilation targeting Java 17/21 compatibility with zero reflective access warnings. |
| **Mission Control Dashboard** | User request: Modernize and improvise the dashboard. | Enhance Dashboard with Multi-XPath Synthesizer, Intention Tier filter, Pause/Resume telemetry, and UAT Sign-Off generator. |

---

## 2. Phased Implementation Roadmap

### Phase 1: Core Engine & Multi-XPath Synthesizer Alignment (KS 11.6.0)
- [ ] **Task 1.1**: Enhance `DynamicXPathGenerator.java` to generate a structured collection of named XPath strategies (`PRIMARY_SEMANTIC`, `ID_RELATIVE`, `CONTAINER_ASCENSION`, `HIERARCHY_POSITION`, `ATTRIBUTES_MATCH`).
- [ ] **Task 1.2**: Upgrade `KatalonRsSynthesizer.java` to write full `<selectorCollection>` and `<webElementProperties>` with named XPath entries mirroring Katalon 11.6.0's Object XPaths table for both Web and Mobile.
- [ ] **Task 1.3**: Update `KatalonRsSynthesizerTest.java` and `DynamicXPathGeneratorTest.java` to verify multi-locator XML synthesis.

### Phase 2: Comprehensive Keyword Intention Classifier (KS 11.2 - 11.7.0)
- [ ] **Task 2.1**: Expand `KeywordIntentionClassifier.java` to exhaustively categorize all 26 `isElement*` keywords as `CONDITIONAL_PROBE`, 26 `assertElement*` keywords as `CHECKPOINT`, and mutative keywords as `INTERACTION`.
- [ ] **Task 2.2**: Update `HealContext.java` and `HealingAuditLedger.java` to capture `intentionTier`, `intentionGroup`, and `executionTags`.
- [ ] **Task 2.3**: Update `KeywordIntentionClassifierTest.java` with comprehensive test vectors covering WebUI and Mobile keywords.

### Phase 3: Pause & Resume Concurrency & Session Freshness
- [ ] **Task 3.1**: Implement `LocaraEngine.resetSessionContext()` and cache TTL check to ensure resumed executions discard stale DOM/XML hierarchies.
- [ ] **Task 3.2**: Verify thread-safety and immediate disk synchronization in `HealingAuditLedger.java`.

### Phase 4: Mission Control Dashboard Improvisation (Dashboard v1.7.0)
- [ ] **Task 4.1**: Update `dashboard/server.py` to rename legacy classes, bump version to `v1.7.0`, and add `/api/synthesize-xpath` and `/api/classify-keyword` endpoints.
- [ ] **Task 4.2**: Update `dashboard/generate_report.py` to generate executive UAT reports with Intention Tier badges and multi-locator tables.
- [ ] **Task 4.3**: Enhance `dashboard/index.html` and `dashboard/css/main.css`:
  - Add Katalon 11.6.0 / 11.7.0 Compatibility Badge.
  - Add Intention Tier filter pills (`ALL`, `INTERACTION`, `CHECKPOINT`, `CONDITIONAL_PROBE`).
  - Add an interactive **Multi-XPath Synthesizer & Inspector** in the Recon Lab tab.
  - Add Suite Execution Status simulator (Running, Paused, Completed).

### Phase 5: Verification, Build & Packaging
- [ ] **Task 5.1**: Run `gradle test` to verify 100% test pass rate across all suites.
- [ ] **Task 5.2**: Build lean `locara-1.7.0.jar` with `gradle jar`.
- [ ] **Task 5.3**: Synchronize jar artifact to `Revamp_M-Smile/Drivers/` and update `README.md`.
