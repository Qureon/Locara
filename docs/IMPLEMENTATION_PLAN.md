# Locara by Qureon — Master Implementation Plan & Architecture Evolution

> **Target Ecosystem**: Katalon Studio (Mobile / Web) & Appium Automation Framework  
> **Core Runtime**: Java 17 / 21 / 24 (Modular JVM Library / Katalon Plug-in)  
> **Current Version**: v1.6.0 Universal Enterprise  
> **Organization**: Qureon Open Source (`Engineering Intelligence`)  
> **Status**: COMPLETED & VERIFIED (52/52 Tests Passing, 100% Mutation Verified)  

---

## 🎯 Executive Summary & Mission

**Locara** *(Locator + Recovery)* adalah *autonomous self-healing & semantic assertion engine* generasi baru yang dirancang khusus untuk menutup kelemahan fatal self-healing konvensional pada mobile test automation.

Alih-alih hanya mengandalkan locator cadangan yang sudah di-spy sebelumnya, **Locara** memiliki kemampuan:
1. **Zero-Failure In-Flight Execution**: Mencegat kegagalan `NoSuchElementException` / `StepFailedException` secara instan saat test case berjalan.
2. **Autonomous DOM/XML Heuristic Analysis**: Mengambil pohon hierarki UI (`driver.getPageSource()`) secara real-time dan menganalisis elemen target menggunakan multi-vector similarity scoring (Text, ID, Content-Desc, Class, Bounds, Sibling Context).
3. **Universal Hybrid Mobile & WebView Engine**: Deteksi dan pemulihan otomatis untuk embedded WebView HTML DOM (Cordova, Capacitor, Ionic, React Native WebViews) dengan dukungan atribut HTML5/ARIA modern.
4. **2D Spatial Proximity & Anchor Geometry**: Menemukan target input atau dropdown berdasarkan kedekatan geometris sumbu X & Y saat elemen kehilangan test-tag atau ID.
5. **Universal Semantic Synonym Tensor**: Kamus sinonim dwibahasa (ID/EN) 100% offline untuk penyesuaian otomatis saat elemen berubah kata (misal *Masuk* $\leftrightarrow$ *Login*, *Lanjut* $\leftrightarrow$ *Next*, *Cari* $\leftrightarrow$ *Search*).
6. **Zero Compile-Time Coupling (Ghost Mode)**: Integrasi berbasis dynamic reflection sehingga proyek pengujian tetap 100% bersih tanpa error kompilasi jika JAR tidak dipasang.
7. **Traceable Audit Ledger**: Mencatat bukti perbaikan, skor keyakinan (*confidence score*), dan screenshot sebelum/sesudah ke dalam berkas log UAT tanpa kebocoran PII.

---

## 🏛️ System Architecture Topology

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          KATALON STUDIO TEST RUNNER                          │
│                                                                             │
│  [Test Case Step] ──► Mobile.tap(findTestObject('...'), 'Lanjut Bayar')    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼ (NoSuchElementException / Failure)
┌─────────────────────────────────────────────────────────────────────────────┐
│                             LOCARA RUNTIME CORE                             │
│                                                                             │
│  ┌───────────────────────┐             ┌─────────────────────────────────┐  │
│  │ 1. Interceptor Layer  │ ──(Fail)──► │ 2. Live PageSource XML/HTML     │  │
│  │    (LocaraHook Guard) │             │    (Appium / MobileDriver)      │  │
│  └───────────────────────┘             └────────────────┬────────────────┘  │
│                                                         │                   │
│                                                         ▼                   │
│  ┌───────────────────────┐             ┌─────────────────────────────────┐  │
│  │ 4. In-Memory Hotfix   │ ◄──(Score)──│ 3. Multi-Vector Heuristic Scorer│  │
│  │    & Action Retry     │             │    - Semantic Fuzzy Text        │  │
│  └───────────┬───────────┘             │    - Universal Synonym Tensor   │  │
│              │                         │    - 2D Spatial Proximity       │  │
│              │                         │    - Danger Polarity Guard      │  │
│              │                         └─────────────────────────────────┘  │
│              ▼                                                              │
│  ┌───────────────────────┐             ┌─────────────────────────────────┐  │
│  │ 5. Dynamic XPath Gen  │             │ 6. Audit Ledger & Evidence      │  │
│  │    & .rs Synthesizer  │             │    (JSON Log + Visual Diff)     │  │
│  └───────────────────────┘             └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📋 Phased Roadmap & Evolutionary Milestones

### 🔹 Phase 1: Core Domain, XML Hierarchy Parser & Heuristic Matcher (v1.0.0)
- [x] Konfigurasi struktur Gradle / Java (Java 17/21 target, group: `com.qureon.locara`).
- [x] Implementasi immutable model `UIElementNode`.
- [x] Parser DOM/StAX native Java ultra-cepat (<50ms).
- [x] Multi-Vector Scoring Engine (Jaro-Winkler, Levenshtein, Token Overlap).

### 🔹 Phase 2: Dynamic XPath Generator & Katalon `.rs` Synthesizer (v1.1.0)
- [x] Generator XPath relatif yang tangguh dan tahan perubahan hierarki.
- [x] Template generator XML standar Katalon Studio (`WebElementEntity` & `MobileElementEntity`).
- [x] Repository Storage Manager dengan aturan `SAFE_CREATE`.

### 🔹 Phase 3: Runtime Interceptor & In-Flight Metaprogramming (v1.2.0 - v1.3.0)
- [x] Katalon `TestObject` in-memory mutator tanpa restart test case.
- [x] Appium Driver Live Bridge untuk eksekusi aksi instan (`click()`, `sendKeys()`).
- [x] `Locara` Master Wrapper API dengan backward-compatibility layer untuk namespace lama `com.zerohealing.*`.

### 🔹 Phase 4: Enterprise Safety, Danger Polarity & Keyboard Airbag (v1.4.0)
- [x] `DangerKeywordGuard`: Proteksi polaritas positif vs destruktif (mencegah klik tombol *Batal*/*Hapus*).
- [x] `KeyboardGuard` (*The Golden Triad*): Deteksi dan auto-dismiss keyboard virtual saat elemen tertutup.
- [x] `ScrollRecoveryEngine`: Auto-scroll cerdas untuk elemen di bawah lipatan layar (*below the fold*).
- [x] `HealingAuditLedger`: Pencatatan log UAT ganda (`healing_audit.json` & `.md`).

### 🔹 Phase 5: Universal 2D Spatial Alignment & Field Relationship (v1.5.0)
- [x] `SpatialProximityScorer`: Evaluasi geometris sumbu X & Y untuk elemen tanpa ID/tag.
- [x] Field Relationship Extractor: Pengenalan pasangan `Key -> Value` pada form transaksi dan struk.
- [x] Autonomous Overlay & Permission Airbag: Netralisasi modal izin sistem OS (`permissioncontroller`).

### 🔹 Phase 6: Universal Hybrid Mobile & Concurrency Hardening (v1.6.0)
- [x] `WebViewContextDetector` & `HtmlDomParser`: Deteksi otomatis dan penyembuhan penuh embedded WebView HTML DOM.
- [x] Concurrency & Parallel Execution Hardening (Thread-safe lock-free ledgers untuk Katalon 11.4+ parallel suite run).
- [x] `KeywordIntentionClassifier`: Klasifikasi keyword baru Katalon 11.7 (`assertElement` vs `isElement`).
- [x] `UniversalSemanticDictionary`: Kamus sinonim semantik universal 15 kluster (ID/EN) 100% offline.
- [x] Zero Compile-Time Dynamic Reflection: Decoupling penuh agar proyek bebas dari error kompilasi di laptop rekan kerja.

---

## 🛡️ Supreme Invariants

1. **Universal & Project-Agnostic**: Engine bekerja 100% pada semantik UI umum tanpa keterikatan pada nama proyek tertentu.
2. **Zero Cloud PII Leakage**: Tidak ada transmisi data keluar mesin lokal. Nol ketergantungan pada API cloud eksternal.
3. **Danger Polarity Invariant**: Jika target hint bermakna positif (*Lanjut*, *Bayar*), engine mutlak menolak elemen destruktif (*Hapus*, *Batal*, *Blokir*).
4. **Strict Concurrency Safety**: Thread-safe pada eksekusi paralel multi-device.
5. **Zero Compile-Time Coupling**: Proyek pengujian harus tetap dapat dikompilasi secara bersih tanpa error meskipun file JAR Locara dicabut dari folder `Drivers/`.

---
*Locara by Qureon — Engineered for Absolute Automation Resilience.*
