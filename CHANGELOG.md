# Changelog — Locara by Qureon

All notable changes to **Locara** are documented in this file.
This project adheres strictly to [Semantic Versioning (SemVer 2.0.0)](https://semver.org/) and the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) specification.

---

## [v1.15.0] - 2026-11-13
### Added
- **Per-Thread Target Isolation API (`Locara` & `HealConfig`)**: Added `setThreadAppTarget(AppTarget)` and `clearThreadAppTarget()` (plus `LocaraHook.IN_FLIGHT.remove()` cleanup) so concurrent test runners never mutate or leak global profile state.
- **Mission Control Persistence & Hardened Security (`dashboard/server.py`, `dashboard/generate_report.py`, `dashboard/js/utils.js`)**: Added allowlisted `locara.properties` persistence (`load_persisted_config` / `save_persisted_config`), `generate_report(output_path, audit_path)` override, dynamic `LOCARA_AUDIT_DIRS` discovery, shared HTML/XML/XPath escaping (`escapeHtml`, `escapeXml`, `toXPathLiteral`, `escapeCodeString`), loopback CORS + Origin/Host CSRF validation + small-body drain on `403`, and `2 MiB` request size enforcement.
- **Cross-Lingual Multi-Word Concept Matching & Build Portability**: Wired multi-word phrase token scoring in `UniversalSemanticDictionary.calculateSemanticAffinity`, cross-card entity isolation in `ContextualEntityResolver`, bundled Gradle 9.8.2 Wrapper (`gradlew`, `gradlew.bat`) with `-Xlint:unchecked`, and 19 new regression tests across [RegressionHardeningTest.java](src/test/java/com/qureon/locara/RegressionHardeningTest.java) (110/110 JUnit passing) and [test_dashboard_server.py](dashboard/tests/test_dashboard_server.py) (8/8 Python `unittest` passing).

### Fixed
- **False-Green Elimination & Mock Realism**: Removed false-green exception catches in `Locara.verifyElementExists`, `verifyElementDisplayed`, `healAndVerifyVisible`, `CucumberLocara.verifyText`, `LocaraTestListener`, `KatalonDriverBridge`, Playwright/Cucumber TypeScript adapters, and fixed `AppiumLocaraTest.MockAppiumDriver` so broken locators genuinely exercise autonomous healing.
- **XXE, ReDoS, Path Traversal & XSS Defenses**: Blocked `<!DOCTYPE`/`<!ENTITY` payloads in `PageSourceParser` (`ThreadLocal<DocumentBuilder>`) and `dashboard/server.py`, hardened regexes against backtracking in `HtmlDomParser` and `KmpMorphologyResolver`, blocked path traversal & unescaped descriptions in `KatalonRsSynthesizer`, synchronized CRLF `<webElementProperties>` updates in `LocaraOrSanitizer`, replaced token-in-URL auth with `gh auth setup-git` in `tools/sync-wiki.sh`, removed inline `onclick` DOM injection in `dashboard/js/modules/telemetry.js`, unified canonical `locara_audit.json` output in `HealingAuditLedger`, and scrubbed personal/corporate test artifacts.

---

## [v1.14.0] - 2026-11-13
### Added
- **Code Naming Intelligence & Convention Standardization Engine (`KatalonRsSynthesizer`)**: Added `deriveSemanticObjectName` enforcing standardized widget category prefixes (`Btn*`, `Txt*`, `Inp*`, `Pnl*`, `Img*`, `Chk*`, `Rdo*`) and semantic PascalCase labels via `UniversalSemanticDictionary.isEstablishedDomainAcronym` and `expandCrypticAbbreviation`, eliminating generic names (`01. ViewGroup_3`, `Button_Submit`).
- **Autonomous Variable Synthesizer (AVS) Hygiene Guard (`AutoVariableSynthesizer`)**: Added `isValidDomainVariableName` enforcing lowercase identifiers without generic placeholders (`data`, `temp`, `val`, `value`, `item`, `object`, `obj`, `result`, `x`, `abc`) during `.tc` Menu Variable synthesis.
- **Domain Acronyms & Abbreviation Policy Tensor (`UniversalSemanticDictionary`)**: Added `isEstablishedDomainAcronym` for preserving critical domain acronyms (`QRIS`, `RTGS`, `BI-FAST`, `SKN`, `MPIN`, `VA`, `OTP`, `KMP`) and `expandCrypticAbbreviation` for expanding ambiguous short forms (`rek` -> `rekening`, `trx` -> `transaksi`, `no` -> `nomor`).
- **Code Naming Auditor CLI Subcommand (`locara audit-naming`)**: Introduced `CodeNamingAuditor` in `LocaraCli` allowing developers to execute Mode A (Audit Only) scans across Object Repositories, Test Cases, and Groovy scripts to produce Markdown inventory reports.

---

## [v1.13.0] - 2026-11-13
### Added
- **Dynamic Multi-Account Entity Resolution (`ContextualEntityResolver.resolveAccountByNumber`)**: Added deterministic filtering by `targetAccountNumber` to resolve the specific active bank account card from multi-account lists (`HorizontalScrollView`, `RecyclerView`, `my_account_savings_*`). Guarantees that automated tests in multi-account scenarios target the exact account under test rather than blindly defaulting to card index 0.
- **Centroid-Safe Account Card Tap Synthesizer (`ContextualEntityResolver.resolveSafeCardTapXPath`)**: Synthesizes safe XPath selectors that target non-interactive card visual elements (`..._art`, `..._title`, or `android.widget.ImageView`). Completely bypasses Appium centroid tapping vulnerabilities where tapping composite card bounds strikes child action buttons (`_copy_button` or `_eye_button`), preventing false-stay screen lockups.
- **Public Card Navigation API (`Locara.tapAccountCard`, `Locara.getSafeCardTapXPath`)**: Drop-in methods in `Locara.java` for Katalon Studio test cases to navigate safely from account lists directly to account details.

---

## [v1.12.0] - 2026-11-09
### Added
- **Corporate Issuer Header vs Instrument Isolation (`DangerKeywordGuard`)**: Added Rule 0B enforcing strict semantic entity isolation between corporate legal entity headers (`PT ... Aset Manajemen / Investment Management / Sekuritas`) and financial instruments / product cards. Strictly blocks false-healing of product target queries (e.g. `reksa dana manulife dana campuran`) to corporate issuer company headers (e.g. `PT Manulife Aset Manajemen Indonesia`), reducing cross-entity false positives to 0.0%.
- **Object Repository Strategy-Method Conflict Harmonizer (`LocaraOrSanitizer`)**: Enhanced `.rs` sanitizer to detect and harmonize contradictory selector configurations where `<selectorMethod>BASIC</selectorMethod>` or `<locatorStrategy>ATTRIBUTES</locatorStrategy>` co-exists with a populated relative XPath `<locator>(//.*)</locator>`. Automatically aligns `<selectorMethod>` and `<locatorStrategy>` to `XPATH`, preventing Katalon Studio from ignoring custom XPath locators.

---

## [v1.11.0] - 2026-11-09
### Added
- **Contextual Entity Resolver (`ContextualEntityResolver` & `ResolvedAccount`)**: Autonomous mobile banking dashboard entity extraction. Discovers active account numbers, account holder names, account types (e.g. Tabungan Mega Dana, Mega Payroll), and balance visibility states directly from Appium page source / Android UI hierarchy XML (`HorizontalScrollView`, `ViewGroup`, `RecyclerView`). Extracts clean 10–16 digit account numbers even from spaced card representations (`9000 4002 1025 853` -> `900040021025853`).
- **Autonomous Variable Synthesizer (`AutoVariableSynthesizer`)**: Programmatic management and synchronization of Katalon Studio Menu Variables (`<variableLinks>` and `<variable>`) inside `.tc` XML entities with deterministic UUID v4 generation, schema validation, and zero duplication. Prevents blank variable lists in Katalon Studio UI and guarantees seamless data binding.
- **Script Variable Scoping Aligner (`ScriptVariableAligner`)**: Enforces variable declarations (`passwordLogin`, `nomorRekeningSumber`, etc.) to be defined outside `try { ... }` blocks in Groovy test scripts, ensuring clean variable scoping, persistence across `catch`/`finally`, and standard compliance.
- **Expanded CLI & Java Public API (`Locara`, `LocaraCli`)**: Added `extract-account`, `sync-tc`, and `align-script` commands to `locara` CLI and corresponding public methods `Locara.resolveActiveAccount(...)`, `Locara.synthesizeTestCaseVariables(...)`, `Locara.syncVariableDefault(...)`, and `Locara.alignScript(...)`.

---

## [v1.10.0] - 2026-11-05
### Added
- **Wealth & Investment Financial Polarity Protection (`DangerKeywordGuard`)**: Enriched `FINANCIAL_KEYWORDS` with wealth management transaction actions (`subscribe`, `subscription`, `langganan`, `penambahan unit`, `redeem`, `redemption`, `cairkan`, `pencairan`, `jual unit`, `switching`, `pengalihan`). Rule 0 strictly blocks accidental clicks on transaction action buttons when automated tests or inquiries search for informative tabs or buttons (`Deskripsi`, `Dokumen`, `Kinerja`, `Bandingkan`, `Informasi`).
- **Wealth Management Universal Semantic Tensor (`UniversalSemanticDictionary`)**: Added 8 comprehensive micro-tensor semantic clusters (Concepts 18 to 25) covering Investment Subscriptions (`subscribe`, `investasi`, `beli unit`), Redemptions (`redeem`, `cairkan`), Performance (`kinerja`, `performance`, `return`, `imbal hasil`), Documents (`prospektus`, `prospectus`, `fund fact sheet`, `ffs`), Description (`deskripsi`, `about`, `detail produk`), Comparison (`bandingkan`, `benchmark`), Asset Categories (`pasar uang`, `pendapatan tetap`, `campuran`, `saham`, `obligasi`, `sbn ritel`), and Risk Profiles (`profil risiko`, `risk profile`, `sid`).
- **Horizontal Product Carousel & Tab Strip Traversal (`ScrollRecoveryEngine`)**: Added autonomous horizontal carousel swipe recovery (`performSwipeLeft` and `performSwipeRight`) and detection (`hasHorizontalCarousel` and `isCarouselEligible`) to dynamically unveil off-screen investment cards and product tabs across `HorizontalScrollView`, `ViewPager2`, and horizontal `RecyclerView` structures without triggering false swipes during read-only probes.
- **Market Hours & Advisory Modal Overlay Neutralizer (`OverlayAirbag`)**: Expanded `DISMISS_MODAL_TERMS` to recognize investment notice buttons (`"saya mengerti"`, `"mengerti"`, `"paham"`, `"ok"`, `"oke"`, `"setuju"`), ensuring market hours closures and banking advisories are autonomously neutralized without disrupting test flows.

---

## [v1.9.4] - 2026-11-05
### Added
- **Smart `TestObject` Property & Locator Hint Fallback (`Locara`)**: Added `resolveEffectiveHint` and `getMobileLocator()` extraction so generic `.rs` names (`01. ViewGroup`) automatically resolve semantic hints from `text`/`content-desc` properties or `@text='...'` XPath literals.
- **Complete 38-Release SemVer `CHANGELOG.md`**: Full historical changelog from `v1.0.0` through `v1.9.4`.

### Fixed
- **Post-Miss `OverlayAirbag` Recovery & Negative Probe Guard (`Locara`, `LocaraHook`)**: Triggered `OVERLAY_AIRBAG.dismissBlockingOverlay` automatically inside `Locara.executeHealing` when initial heuristic matching fails before scroll fallback, and synchronized negative test detection in `LocaraHook.groovy`.

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
- **Mission Control Dashboard Escaping & Report Sanitization (`dashboard/`, `HtmlDomParser`)**: Added initial HTML entity escaping utilities (`escapeHtml`) across dashboard views, `generate_report.py` output sanitization, and attribute hardening in `HtmlDomParser` (completed across all routes with payload size & loopback CORS enforcement in `v1.15.0`).

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
