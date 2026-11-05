# Changelog — Locara by Qureon

All notable changes to **Locara** are documented in this file.
This project adheres strictly to [Semantic Versioning (SemVer 2.0.0)](https://semver.org/) and the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) specification.

---

## [v1.9.4] - 2026-11-05
### Added
- **Smart `TestObject` Property & Locator Hint Fallback (`Locara`)**: Added `resolveEffectiveHint` and `getMobileLocator()` extraction so generic `.rs` names (`01. ViewGroup`) automatically resolve semantic hints from `text`/`content-desc` properties or `@text='...'` XPath literals.
- **Complete 38-Release SemVer `CHANGELOG.md`**: Full historical changelog from `v1.0.0` through `v1.9.4`.

### Fixed
- **Pre-Healing `OverlayAirbag` Invocation (`Locara`)**: Added `OVERLAY_AIRBAG.dismissBlockingOverlay` prior to XML snapshot extraction inside `Locara.executeHealing` (`tap`, `setText`, `clearText`), and synchronized `isNegativeTestCaseRunning()` in `LocaraHook.groovy`.

---

## [v1.9.3] - 2026-11-05
### Added
- **Flat-Form Adjacent Sibling Axis XPath Synthesis (`DynamicXPathGenerator`)**: Added `following-sibling::...[1]` and `preceding-sibling::...[1]` synthesis when multiple anonymous `EditText` or `CheckBox` elements share a single flat parent or grandparent `ViewGroup`.

### Fixed
- **XML Entity Escaping in `.rs` Sanitizer (`LocaraOrSanitizer`)**: Added XML entity escaping/unescaping (`&amp;`, `&lt;`, `&gt;`) when patching `<locator>` tags in `.rs` files.

---

## [v1.9.2] - 2026-11-05
### Fixed
- **Conflicting Multi-Word Banking Qualifier & Disjoint Numeric Guards (`StringSimilarity`)**: Added `hasConflictingDistinguishingTokens` (capping similarity at `0.55` for `"Transfer RTGS"` vs `"Transfer SKN"` or `"Nomor Rekening Sumber"` vs `"Nomor Rekening Tujuan"`) and `hasDisjointNumericTokens` (capping similarity at `0.40` for `"Tenor 3 Bulan"` vs `"Tenor 6 Bulan"`).
- **React Native Container Danger Guard (`DangerKeywordGuard`)**: Added `node.getAggregatedDescendantText(2)` to `extractNodeTokens` so clickable `ViewGroup` wrappers around destructive/financial labels are strictly evaluated.

---

## [v1.9.1] - 2026-11-05
### Fixed
- **`.rs` Strategy Preservation on Ascended Containers & Relational XPaths (`KatalonRsSynthesizer`, `KatalonDriverBridge`)**: Added `isComplexOrRelationalXPath(node, xpath)` to preserve `<locatorStrategy>XPATH</locatorStrategy>` on React Native parent-ascended containers (`.//`), sibling axes, and indexed XPaths, and prioritized healed XPaths over raw `/hierarchy` paths in `buildPropertyList`.

---

## [v1.9.0] - 2026-11-04
### Added
- **Anonymous Widget Healing & 360-Degree Spatial Intelligence (`SpatialAlignmentEngine`, `HeuristicMatcher`, `DynamicXPathGenerator`)**: Full recovery for interactive `ViewGroup`, `CheckBox`, and `EditText` elements lacking `resource-id`, `text`, and `content-desc` across all 4 cardinal directions (`TOP_TO_BOTTOM`, `LEFT_TO_RIGHT`, `RIGHT_TO_LEFT`, `BOTTOM_TO_TOP`) plus Single-Widget Type Fallback.

---

## [v1.8.3] - 2026-11-03
### Fixed
- **Mission Control DOM XSS Remediation & HTTP Payload Hardening (`dashboard/`, `HtmlDomParser`)**: Replaced unsafe `innerHTML` rendering with safe DOM construction, enforced HTTP request body limits in `dashboard/server.py`, and hardened HTML/XML attribute parsing.

---

## [v1.8.2] - 2026-11-03
### Fixed
- **XPath Single-Quote `concat(...)` Escaping & Multi-Driver Reflection (`LocaraOrSanitizer`, `KatalonDriverBridge`, `AppiumLocara`)**: Fixed single-quote XPath literal escaping and improved reflection fallback across Appium 1.x and 2.x drivers.

---

## [v1.8.1] - 2026-11-03
### Added
- **Rule 0 Financial Action Polarity Shield (`DangerKeywordGuard`)**: Added `FINANCIAL_ACTION_KEYWORDS` lockout preventing neutral/navigation hints from healing onto financial submission buttons (`Bayar`, `Transfer`, `Kirim`, `Topup`, `Beli`).
- **Thread-Safe Multi-Runner Target Isolation (`HealConfig`)**: Upgraded `AppTarget` storage to `ThreadLocal<AppTarget>` for concurrent Test Suite Collection execution.

---

## [v1.8.0] - 2026-11-03
### Fixed
- **Single-Word Probe False-Positive Guard (`StringSimilarity`, `LocaraIntentMatcher`, `UniversalSemanticDictionary`)**: Eliminated single-word 100% token overlap false positives against multi-word error banners and decoupled navigation vs financial synonym clusters.

---

## [v1.7.4] - 2026-11-02
### Added
- **First-Class Cucumber BDD & Gherkin Engine (`CucumberLocara`, `@qureon/cucumber-locara`)**: Bilingual (Indonesian & English) natural-language Gherkin step intent parser (`CLICK`, `SET_TEXT`, `VERIFY_VISIBLE`) for Cucumber-JVM and Cucumber-JS.

---

## [v1.7.3] - 2026-11-02
### Added
- **First-Class Microsoft Playwright Integration (`PlaywrightLocara`, `@qureon/playwright-locara`)**: Standalone Java and TypeScript/JS Playwright self-healing wrappers, WAI-ARIA role/label heuristic DOM matcher, Playwright MCP adapter, and Azure Playwright Testing config.

---

## [v1.7.2] - 2026-11-02
### Changed
- **Canonical `locara.*` Property Prefix (`HealConfig`, `LocaraHook`)**: Standardized configuration keys under `locara.*` while preserving 100% backward compatibility with legacy `qureon.*` and `healing.*` prefixes.

---

## [v1.7.1] - 2026-11-02
### Fixed
- **Anti-Scroll Modal/Overlay Guard (`ScrollRecoveryEngine`)**: Blocked auto-scroll recovery when a transient modal, dialog, bottom sheet, or OS permission popup is active, and restricted presence probes to strict read-only evaluation.

---

## [v1.7.0] - 2026-11-02
### Added
- **Katalon Studio 11.6 & 11.7 Ranked Multi-XPath Synthesizer (`KatalonRsSynthesizer`, `KeywordIntentionClassifier`)**: Added ranked `webElementXpaths` table generation (`xpath:attributes`, `xpath:idRelative`, `xpath:position`, `xpath:customAttributes`) and support for 26 `isElement*`/`assertElement*` state keywords.

---

## [v1.6.2] - 2026-11-02
### Removed
- **Legacy `com.zerohealing.*` Package Purge**: Removed 41 deprecated duplicate compatibility classes to enforce a single canonical `com.qureon.locara.*` namespace.

---

## [v1.6.1] - 2026-11-02
### Added
- **Ghost Mode Zero-Compile-Coupling Documentation**: Added comprehensive documentation for dynamic reflection execution and team coexistence (`docs/USER_TUTORIAL_GUIDE.md`).

---

## [v1.6.0] - 2026-11-02
### Added
- **Universal Hybrid WebView Engine (`WebViewContextDetector`, `HtmlDomParser`, `UniversalSemanticDictionary`)**: Automatic detection and self-healing for embedded HTML DOM WebViews (Cordova, Capacitor, Ionic, React Native WebView) and offline 15-cluster bilingual synonym tensor.

---

## [v1.5.3] - 2026-10-30
### Added
- **GitHub Wiki Documentation Suite & Qureon Brand Assets**: Added wiki documentation pages, sanitized markdown footers, and added official vector brand assets.

---

## [v1.5.2] - 2026-10-30
### Added
- **3-Tier `AppTarget` Preset Switcher (`AppTarget`, `HealConfig`)**: Added `UMUM`, `REVAMP`, and `KMP` target presets and removed deprecated template files.

---

## [v1.5.1] - 2026-10-30
### Added
- **KMP Morphology Resolver (`KmpMorphologyResolver`, `LocaraIntentMatcher`)**: Added Kotlin Multiplatform resource-id suffix affinity scoring (`_button`, `_input`, `_field`) and dynamic template token resolution (`<key>`, `<i>`).

---

## [v1.5.0] - 2026-10-30
### Added
- **Universal 2D Spatial Alignment, Field Relationship Parser & Overlay Airbag (`SpatialAlignmentEngine`, `FieldRelationshipParser`, `OverlayAirbag`)**: Geometric X/Y proximity scoring, `Key -> Value` receipt pair extraction, and automatic Android permission/promo modal dismissal.

---

## [v1.4.1] - 2026-10-30
### Fixed
- **Presence Probe Object ID Logging (`LocaraHook`)**: Fixed `objId` formatting in presence probe interception log messages.

---

## [v1.4.0] - 2026-10-30
### Added
- **Autonomous Presence Probing, Semantic Intent Matcher & OR Sanitizer (`Locara.healAndProbePresent`, `LocaraIntentMatcher`, `LocaraOrSanitizer`)**: Added in-flight recovery for `verifyElementPresent`/`waitForElementPresent` and automated `/hierarchy` XPath sanitization in `.rs` files.

---

## [v1.3.3] - 2026-10-30
### Fixed
- **`LocaraHook` Master Switch Priority (`LocaraHook`, `README.md`)**: Prioritized `locara.enabled` in `LocaraHook` properties loader and synchronized configuration documentation.

---

## [v1.3.2] - 2026-10-30
### Fixed
- **Primary `locara.enabled` Master Switch (`HealConfig`)**: Added primary master switch support for `locara.enabled` in `HealConfig`.

---

## [v1.3.1] - 2026-10-30
### Fixed
- **Test Listener Engine Binding (`LocaraTestListener`)**: Bound `LocaraEngine` directly in `LocaraTestListener` and fixed header path comments.

---

## [v1.3.0] - 2026-10-29
### Added
- **Standalone Reconnaissance CLI & E2E Simulation Suites (`LocaraCli`, `LocaraEngineE2ETest`)**: Added command-line tool (`--xml`, `--hint`, `--out`, `--inspect`, `--sanitize-or`), E2E test suites, and `docs/ARCHITECTURE_AND_FLOW.md`.

---

## [v1.2.4] - 2026-10-27
### Fixed
- **Backward Compatibility Bridge & `.rs` Strategy Selection (`KatalonRsSynthesizer`, `HealConfig`)**: Added transitional compatibility bridge and refined native `MobileElementEntity` strategy selection.

---

## [v1.2.3] - 2026-10-24
### Added
- **UAT Sign-Off Report Generator (`dashboard/generate_report.py`)**: Added standalone HTML/PDF UAT compliance certificate generator and symbol tokenization normalization.

---

## [v1.2.2] - 2026-10-22
### Added
- **Modular Mission Control Web Dashboard (`dashboard/server.py`, `dashboard/index.html`)**: Added local telemetry HTTP server, visual bounding-box inspector, and live filter feed.

---

## [v1.2.1] - 2026-10-19
### Added
- **Thread-Safe Dual Telemetry Audit Ledger (`HealingAuditLedger`)**: Added atomic JSON and Markdown healing audit ledgers with automatic PII masking.

---

## [v1.2.0] - 2026-10-15
### Added
- **Autonomous Mobile Framework Detector (`FrameworkDetector`, `FrameworkTuningProfile`)**: Automatic fingerprinting and adaptive weight tuning for React Native, Jetpack Compose, Flutter, Native Android XML, and iOS XCUITest.

---

## [v1.1.3] - 2026-10-09
### Fixed
- **Native `KeywordExecutor` Cache Interception & `CustomKeywords` Wrapper (`LocaraKeywords.groovy`)**: Added Katalon `CustomKeywords` wrapper and cached reflection dispatch.

---

## [v1.1.2] - 2026-10-05
### Fixed
- **Test Listener Lifecycle Integration (`LocaraTestListener.groovy`)**: Added `@BeforeTestSuite` and `@AfterTestSuite` lifecycle hooks and resolved `SupportLevel` imports.

---

## [v1.1.1] - 2026-10-02
### Added
- **Transparent In-Flight Groovy Hook & Standalone Appium API (`LocaraHook.groovy`, `Locara.java`, `AppiumLocara.java`)**: Added zero-code-change Groovy `KeywordExecutor` interceptor and standalone Appium driver wrapper.

---

## [v1.1.0] - 2026-09-22
### Added
- **Dynamic XPath Generator & Katalon `.rs` Synthesizer (`DynamicXPathGenerator`, `KatalonRsSynthesizer`, `VisualEvidenceAnnotator`)**: Added 7-tier relative XPath generator, `.rs` XML synthesizer with `.healed.rs` quarantine, and bounding-box screenshot annotator.

---

## [v1.0.2] - 2026-09-10
### Fixed
- **The Golden Triad Keyboard Occlusion Guard & Auto-Scroll Recovery (`KeyboardGuard`, `ScrollRecoveryEngine`)**: Added automatic virtual keyboard dismissal (`y > 58%`) and viewport swipe-down recovery.

---

## [v1.0.1] - 2026-08-28
### Fixed
- **Danger Polarity Lockout Guard (`DangerKeywordGuard`)**: Enforced strict polarity lockout preventing positive action hints from healing onto destructive buttons (`Batal`, `Hapus`, `Blokir`, `Tutup`).

---

## [v1.0.0] - 2026-08-15
### Added
- **Genesis Core Heuristic Engine (`LocaraEngine`, `PageSourceParser`, `HeuristicMatcher`, `StringSimilarity`)**: Fast StAX/DOM XML hierarchy parser (`<50ms`) and multi-vector similarity scoring (Jaro-Winkler, Levenshtein, Token Overlap).
