# Locara by Qureon

<div align="center">

### Adaptive Locator Intelligence & Universal Self-Healing Engine
*An autonomous, offline-first self-healing engine for resilient mobile & hybrid test automation.*

[![Version](https://img.shields.io/badge/version-1.8.1-00E5FF?style=flat-square&logo=semver)](https://github.com/Qureon/Locara/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg?style=flat-square)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-63%2F63%20passing-10B981?style=flat-square)](build.gradle)
[![Java](https://img.shields.io/badge/java-17%20%7C%2021%20%7C%2024-orange?style=flat-square&logo=openjdk)](build.gradle)
[![Katalon](https://img.shields.io/badge/katalon-8.x%20%7C%209.x%20%7C%2010.x%20%7C%2011.x%20(11.7.0%2B%20Ready)-00B0FF?style=flat-square)](https://katalon.com)
[![Appium](https://img.shields.io/badge/appium-2.x%20%7C%201.x-662d91?style=flat-square&logo=appium)](https://appium.io)
[![Playwright](https://img.shields.io/badge/playwright-v1.40%2B%20%7C%20MCP%20%7C%20Azure-2EAD33?style=flat-square&logo=playwright)](integrations/playwright/)
[![Cucumber](https://img.shields.io/badge/cucumber-BDD%20%7C%20Gherkin%20%7C%20GenAI-23D96C?style=flat-square&logo=cucumber)](integrations/cucumber/)

[Releases](https://github.com/Qureon/Locara/releases) • [Architecture & Flow](docs/ARCHITECTURE_AND_FLOW.md) • [User Tutorial Guide](docs/USER_TUTORIAL_GUIDE.md) • [Mission Control Dashboard](dashboard/) • [MIT License](LICENSE)

</div>

---

## Overview

**Locara** *(Locator + Recovery)* adalah engine self-healing otonom generasi baru untuk otomasi mobile (Katalon Studio & Appium murni), dikembangkan di bawah naungan **Qureon** (*Engineering Intelligence*).

Locara dirancang untuk mengatasi salah satu masalah terbesar dalam otomasi pengujian mobile enterprise: **flaky test suites akibat perubahan UI mendadak oleh developer** (perubahan ID, layout berpindah, struktur dibungkus kontainer baru, atau halaman WebView).

### Paradigma: In-Flight Autonomous Recovery
Fitur bawaan self-healing pada tool konvensional biasanya hanya mampu mencocokkan locator yang **sudah pernah di-spy** sebelumnya. Jika ID dirombak total atau hierarki baru ditambahkan, test suite akan gagal (*Red Suite*), alur transaksi putus, dan regresi terhenti.

Locara menerapkan prinsip **In-Flight Zero-Interruption**:
1. **In-Flight Dynamic Hotfix (<50ms)**: Saat locator target gagal ditemukan, Locara menganalisis dump XML/HTML layar aktif (`driver.getPageSource()`), menghitung vektor kemiripan heuristik multi-dimensi, menyuntikkan XPath baru ke memori, dan melanjutkan eksekusi tanpa tes berstatus *FAIL*.
2. **Zero-Code Change via Groovy Metaprogramming**: Pengujian tetap menulis kode standar Katalon `Mobile.tap(...)`, `Mobile.setText(...)`, dan `Mobile.getText(...)`. Seluruh interceptor bekerja transparan di latar belakang.
3. **Zero Compile-Time Coupling (Ghost Mode)**: Integrasi berbasis dynamic reflection. Proyek tim **100% HIJAU (0 compile errors)** baik ketika JAR Locara ada maupun saat JAR tidak dipasang di komputer rekan kerja.
4. **Universal 2D Spatial Alignment (v1.5.0)**: Menemukan target input atau dropdown berdasarkan kedekatan geometris sumbu X & Y (Left-to-Right & Top-to-Bottom) saat elemen kehilangan test-tag atau ID.
5. **Universal Hybrid Mobile & WebView Engine (v1.6.0)**: Deteksi otomatis dan self-healing penuh pada embedded WebView HTML DOM (Cordova, Capacitor, Ionic, React Native WebViews) tanpa dependensi eksternal.
6. **Katalon 11.6.0+ Ranked Multi-XPath Synthesizer (v1.7.0)**: Menghasilkan struktur `.rs` XML dengan tabel *Object's XPaths* berlapis (`xpath:attributes`, `xpath:idRelative`, `xpath:position`, `xpath:custom`) yang kompatibel penuh dengan fitur edit locator di Katalon Studio 11.6.0+.
7. **Katalon 11.2 - 11.7.0+ Element State Keywords Hub (v1.7.0)**: Klasifikasi dan penanganan otonom untuk 26 keyword baru `isElement*` (conditional branching non-fatal) dan `assertElement*` (strict assertion checkpoints).
8. **Pause & Resume Concurrency Resilience (v1.7.0)**: Manajemen sesi aman untuk fitur *Pause/Resume Test Suite* Katalon 11.6.0 dengan ledger disk-flush atomik dan invalidasi context otomatis.
9. **Enterprise Financial Shield & Zero-False-Green Lattice (v1.8.0)**: Proteksi polaritas transaksi finansial (*Rule 0: anti-salah klik bayar/transfer*), eliminasi asimetri token tunggal pada probe presence, pemisahan kluster semantik finansial, dan isolasi thread-safe per-runner via `ThreadLocal<AppTarget>` multi-threading.
10. **Enterprise Safety Guard**: Proteksi polaritas bahaya (*Danger Polarity Lockout*), auto-dismiss keyboard virtual (*The Golden Triad*), dan *Auto-Scroll Viewport Fallback* untuk tombol di bawah lipatan layar.
11. **Zero Cloud PII Leakage**: 100% offline, nol transmisi data keluar mesin lokal. Aman untuk standar kepatuhan perbankan & enterprise.

---

## Arsitektur Alur Kerja Locara

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         MOBILE TEST CASE RUNNING                            │
│                                                                             │
│        Mobile.tap(findTestObject('Object Repository/.../btn_Lanjut'), 10)   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼ (Locator Berubah / ID Hilang)
┌─────────────────────────────────────────────────────────────────────────────┐
│                     LOCARA RUNTIME ENGINE INTERCEPTOR                       │
│                                                                             │
│  1. In-Flight Hook (LocaraHook) menyambar StepFailedException               │
│  2. Tarik dump XML/HTML layar aktif via driver.getPageSource() (<50ms)      │
│  3. Framework Detector: Native Android / iOS / Flutter / Compose / WebView  │
│  4. Heuristic Multi-Vector Scoring (Levenshtein, Jaro-Winkler, 2D Spatial)   │
│  5. Universal Semantic Dictionary: Penyesuaian sinonim kata lintas bahasa   │
│  6. Safety Polarity Guard: Cegah salah klik tombol 'Batal' / 'Hapus'        │
│  7. Dynamic XPath Generator menghasilkan selector optimal & presisi         │
│  8. The Golden Triad Keyboard Guard: Tutup keyboard jika tombol terhalang   │
│  9. In-Memory TestObject Mutation + Driver Action Re-execution              │
│ 10. Background Task: Catat bukti sukses ke Audit Ledger (Zero PII)          │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                   TEST CASE TETAP LANJUT KE STEP BERIKUTNYA!                │
│                                                                             │
│               [Input PIN] ──► [Struk Keluar] ──► PASSED (HIJAU)             │
└─────────────────────────────────────────────────────────────────────────────┘
```

> 📖 **Pelajari detail selengkapnya:** Kunjungi [Arsitektur Lengkap & Diagram Alur (docs/ARCHITECTURE_AND_FLOW.md)](docs/ARCHITECTURE_AND_FLOW.md).

---

## Panduan Penggunaan & Instalasi

### Mode 1: Invisible In-Flight Hook (Rekomendasi Utama)
> **100% Zero-Code Change & Zero Compile Error.** Seluruh script lama maupun baru tetap menulis `Mobile.tap(...)` dan `Mobile.setText(...)`.

#### Cara A: Instalasi Mandiri via AI Agent (Paling Mudah)
Jika Anda menggunakan AI assistant (Antigravity IDE, Cursor, Claude Code, GitHub Copilot):
Salin prompt berikut dan tempel ke chat AI Anda:

```markdown
Tolong unduh engine test automation offline "Locara" versi rilis terbaru dari repositori GitHub publik:
https://github.com/Qureon/Locara/releases/latest

Langkah yang perlu kamu lakukan:
1. Cek tag rilis terbaru dari API GitHub (misal v1.6.0).
2. Download asset file .jar biner (contoh: locara-1.6.0.jar) dari Releases tersebut.
3. Simpan file .jar tersebut langsung ke dalam folder: Drivers/ di root workspace proyek Katalon ini.
4. Hapus versi locara-*.jar lama jika sebelumnya ada di dalam folder Drivers/.
5. Pastikan ukuran file terunduh sekitar ~200 KB dan tidak korup.

Catatan: Proyek ini sudah dirancang plug-and-play, jadi kamu tidak perlu mengubah kode Groovy apapun. Cukup tempatkan file .jar di Drivers/ dan ingatkan saya untuk klik 'Project -> Clean...' di Katalon jika diperlukan.
```

#### Cara B: 1-Line Command di Terminal / PowerShell

**Di Windows (PowerShell):**
```powershell
$tag = (Invoke-RestMethod "https://api.github.com/repos/Qureon/Locara/releases/latest").tag_name; $ver = $tag.TrimStart('v'); Invoke-WebRequest -Uri "https://github.com/Qureon/Locara/releases/download/$tag/locara-$ver.jar" -OutFile "Drivers\locara-$ver.jar"; Write-Host "✅ Locara $tag berhasil disuntikkan ke Drivers!" -ForegroundColor Green
```

**Di macOS / Linux (Terminal / Bash):**
```bash
TAG=$(curl -s https://api.github.com/repos/Qureon/Locara/releases/latest | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/') && curl -sL -o Drivers/locara-${TAG#v}.jar https://github.com/Qureon/Locara/releases/download/$TAG/locara-${TAG#v}.jar && echo "✅ Locara $TAG berhasil disuntikkan ke Drivers!"
```

#### Integrasi Katalon Studio:
1. Salin [`LocaraHook.groovy`](integrations/katalon/LocaraHook.groovy) ke dalam folder `Keywords/core/` (atau folder Keywords Anda).
2. Panggil di awal test suite atau Test Listener (`Test Listeners/GlobalSetupListener.groovy`):
```groovy
class GlobalSetupListener {
    @BeforeTestSuite
    def setupTestSuite(TestSuiteContext testSuiteContext) {
        core.LocaraHook.install() // Auto-detect JAR & inisialisasi in-flight hook
    }
}
```

---

### Mode 2: Direct Smart Wrapper (Katalon Studio)
Gunakan API `Locara` secara eksplisit saat merancang screen flow baru:

```groovy
import com.qureon.locara.katalon.Locara
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject

// Skenario A: Objek sudah ada di OR tapi locatornya rusak
Locara.tap(findTestObject('Object Repository/Transfer/btn_Lanjut'), 'Lanjut Transfer')

// Skenario B: Zero-Spy Automation (Objek belum pernah di-spy sama sekali)
Locara.tapByHint('Konfirmasi Bayar', 'Object Repository/Transfer/btn_Konfirmasi')

// Skenario C: Input Field M-PIN / Nominal
Locara.setText(findTestObject('Object Repository/Transfer/input_MPIN'), '123456', 'Masukkan M-PIN')
```

---

### Mode 3: Standalone Appium (Java / Kotlin / TestNG / Cucumber)
Jika Anda menggunakan framework Appium di luar Katalon Studio:

```java
import com.qureon.locara.appium.AppiumLocara;
import io.appium.java_client.android.AndroidDriver;

AndroidDriver driver = new AndroidDriver(appiumServerUrl, capabilities);

// 1. Tap dengan auto-recovery
AppiumLocara.tap(driver, "//android.widget.Button[@id='old_btn_next']", "Lanjut Transfer");

// 2. Set text dengan auto-recovery
AppiumLocara.setText(driver, "com.example.app:id/et_pin", "123456", "Masukkan PIN");

// 3. Find element langsung dengan recovery
WebElement healedElement = (WebElement) AppiumLocara.findElement(driver, "btn_bayar", "Bayar Sekarang");
healedElement.click();
```

---

### Mode 4: Standalone Playwright Web (TypeScript & Java / MCP / Azure)
Tersedia integrasi multi-bahasa untuk ekosistem modern Microsoft Playwright Web:

#### A. Modern TypeScript / JavaScript (`@playwright/test`)
```typescript
import { test, expect } from '@qureon/playwright-locara';

test('Web Checkout with Auto-Healing', async ({ page, locara }) => {
  await page.goto('https://checkout.bankmega.com');

  // Klik tombol bermutasi dengan pemulihan semantik instan
  await locara.click('#btn-broken-old-checkout', 'Bayar Sekarang');

  // Isi form input kredensial
  await locara.fill('input#user_input', 'mega_user_01', 'Masukkan User ID');

  // Self-healing locator
  const badge = await locara.locator('.status-badge', 'Konfirmasi Pembayaran');
  await expect(badge).toBeVisible();
});
```

#### B. Java Playwright (`com.microsoft.playwright.Page`)
```java
import com.microsoft.playwright.Page;
import com.qureon.locara.playwright.PlaywrightLocara;

// 1. Click dengan auto-healing WAI-ARIA
PlaywrightLocara.click(page, "#btn-broken-id", "Bayar Sekarang");

// 2. Fill input text dengan auto-healing
PlaywrightLocara.fill(page, "input.user", "mega_user_01", "Masukkan User ID");

// 3. Healed Locator
Locator healed = (Locator) PlaywrightLocara.locator(page, "#broken-selector", "Bayar Sekarang");
```

---

### Mode 5: Autonomous Cucumber BDD (Java & TypeScript / GenAI)
Eksekusi pengujian Behavior-Driven Development (BDD) berbasis Gherkin tanpa takut patah akibat *locator rot* atau *step definition drift*:

#### A. Zero-Boilerplate Gherkin Step (Java Cucumber-JVM)
```java
import com.qureon.locara.cucumber.CucumberLocara;

// Eksekusi langsung kalimat Gherkin dengan auto-parsing semantik & multi-driver healing
CucumberLocara.executeGherkinStep(driver, "When Pengguna menekan tombol \"Bayar Sekarang\"", null);

// Step definition standar dengan self-healing
@When("Pengguna memasukkan {string} pada kolom {string}")
public void fillInput(String textValue, String fieldLabel) {
    CucumberLocara.setText(driver, "", textValue, fieldLabel);
}
```

#### B. Modern Cucumber-JS (`@qureon/cucumber-locara`)
```gherkin
Feature: Pembayaran Tagihan M-Smile

  Scenario: Nasabah melakukan pembayaran
    Given Pengguna berada di halaman tagihan
    When Pengguna memasukkan "1234567890" pada kolom "Nomor Pelanggan"
    And Pengguna menekan tombol "Bayar Sekarang"
    Then Pengguna melihat status "Transaksi Berhasil"
```

---

### Mode 6: Standalone CLI Quick-Recon
Jalankan evaluasi heuristik dan inspeksi langsung dari terminal:

```bash
# 1. Evaluasi & generate file .rs dari berkas XML dump
java -jar locara-1.8.0.jar --xml dump.xml --hint "Lanjut Transfer" --out "Object Repository/Transfer/btn_Lanjut.rs"

# 2. Inspeksi seluruh elemen interaktif dalam XML / HTML
java -jar locara-1.8.0.jar --xml dump.xml --inspect
```

---

## Fitur Utama

| Fitur | Komponen | Fungsi & Manfaat |
|---|---|---|
| **Universal Hybrid WebView** | `WebViewContextDetector.java` & `HtmlDomParser.java` | Deteksi dan self-healing instan untuk embedded HTML DOM (Cordova, Capacitor, React Native WebView) dengan atribut web modern (`data-testid`, `aria-label`). |
| **2D Spatial Proximity** | `SpatialProximityScorer.java` | Evaluasi geometris sumbu X & Y untuk menemukan input atau tombol yang posisinya bergeser berdasarkan tetangga jangkar (*anchor*). |
| **Universal Semantic Tensor** | `UniversalSemanticDictionary.java` | Kamus sinonim 15 kluster (ID & EN) 100% offline untuk penyesuaian otomatis saat kata berubah (*Masuk* $\leftrightarrow$ *Login*). |
| **Danger-Lockout Policy** | `DangerKeywordGuard.java` | Mencegah salah klik fatal. Jika mencari aksi positif (*"Lanjut"* / *"Bayar"*), engine menolak tombol berpolaritas bahaya (*"Batal"*, *"Hapus"*, *"Blokir"*). |
| **The Golden Triad** | `KeyboardGuard.java` | Deteksi posisi elemen yang tertutup keyboard virtual Android/iOS (`y > 58%`), otomatis memanggil dismiss keyboard. |
| **Auto-Scroll Fallback** | `ScrollRecoveryEngine.java` | Otomatis melakukan *swipe down* jika tombol berada di bawah lipatan layar (*below the fold*), lalu re-dump XML. |
| **Parallel Concurrency** | `HealingAuditLedger.java` | Thread-safe atomic file lock untuk eksekusi paralel multi-device (Katalon 11.4+) tanpa korupsi log. |
| **Dual Telemetry Ledger** | `HealingAuditLedger.java` | Mencatat audit log ganda (`healing_audit.json` & `healing_audit.md`) dengan mask PII untuk integrasi CI/CD. |

---

## Konfigurasi (`locara.properties`)

Letakkan file `locara.properties` di root folder proyek untuk tuning engine:

```properties
# Master Switch: Aktifkan atau nonaktifkan engine secara global
locara.enabled=true

# Ambang batas minimal confidence (0.0 - 1.0) untuk mengeksekusi healing
locara.threshold=0.70

# Bobot Scoring Multi-Vector (Total = 1.0)
locara.weight.text=0.35
locara.weight.id=0.25
locara.weight.desc=0.20
locara.weight.class=0.10
locara.weight.proximity=0.10

# Auto-scroll jika elemen form off-screen (pop-up/modal otomatis dilarang scroll)
locara.scroll.enabled=true
locara.scroll.max_attempts=2

# Pengaturan Object Repository (.rs)
locara.rs.generate=true
locara.rs.overwrite=false
locara.rs.backup=true

# Audit Ledger
locara.audit.enabled=true

# Adaptive Framework Detection (React Native, Compose, Flutter, Android XML)
locara.framework.adaptive=true
```

> **Catatan Kompatibilitas**: Format awalan `locara.*` adalah standar baku terbaru. Seluruh awalan warisan lama (`healing.*` dan `qureon.*`) tetap didukung 100% secara *backward-compatible*.

### Opt-Out Lokal (Developer Override)
Jika ada anggota tim yang ingin menonaktifkan self-healing secara lokal di komputernya:
1. Buat file `locara.local.properties` di root proyek:
   ```properties
   locara.enabled=false
   ```
2. File ini terdaftar di `.gitignore` sehingga tidak akan ter-push ke repository remote.

---

## Mission Control Dashboard & UAT Reports

Locara dilengkapi dengan **Mission Control Web Dashboard** berarsitektur modular:

```bash
# Jalankan Telemetry Server Lokal
python3 dashboard/server.py 8088

# Akses via browser: http://localhost:8088
```

Fitur dashboard:
* **Live Telemetry Feed**: Riwayat perbaikan in-flight dengan live filter status, search, dan sorting.
* **Visual Inspector**: Mockup smartphone dengan bounding box, diff locator, dan breakdown bobot multi-vektor.
* **Regression Health**: Analisis visual locator paling volatil (*flaky*).
* **XML & DOM Recon Lab**: Sandbox interaktif untuk menguji pencocokan heuristik dari raw XML maupun HTML DOM.
* **UAT Sign-Off Certificate**: Dokumen sertifikat kepatuhan audit otomatis yang siap dicetak ke PDF.

---

## Struktur Repositori

```
Locara/
├── build.gradle                              # Gradle build configuration (group: com.qureon.locara, Java 17/21)
├── settings.gradle                           # Project settings (rootProject.name = 'locara')
├── README.md                                 # Master Documentation & User Guide
├── LICENSE                                   # MIT License
├── docs/                                     # Technical Documentation & Playbooks
│   ├── ARCHITECTURE_AND_FLOW.md              # Deep-dive Architecture, Scoring Radars & Flow
│   ├── USER_TUTORIAL_GUIDE.md                # Tim Tutorial, Ghost Mode & Troubleshooting
│   ├── IMPLEMENTATION_PLAN.md                # Evolutionary Roadmap & Architecture Baseline
│   └── v1.6_HYBRID_AND_UNIVERSAL_ENTERPRISE_PLAN.md # v1.6.0 Verification & Release Plan
├── dashboard/                                # Mission Control Dashboard & Telemetry Platform
│   ├── assets/                               # Vector SVG Logo & Favicon
│   ├── css/                                  # Modular Stylesheets
│   ├── js/                                   # Modular ES Architecture
│   ├── index.html                            # Master Telemetry & Recon Web UI
│   ├── server.py                             # Python HTTP & live API telemetry server
│   └── generate_report.py                    # Standalone HTML UAT sign-off report generator
├── integrations/
│   └── katalon/
│       ├── LocaraHook.groovy                 # Transparent In-Flight Dynamic Reflection Interceptor
│       ├── LocaraKeywords.groovy             # Native Katalon Custom Keyword wrapper
│       └── LocaraTestListener.groovy         # Test Listener lifecycle integration
└── src/
    ├── main/java/
    │   ├── com/qureon/locara/                # Core Locara by Qureon Engine
    │   │   ├── cli/LocaraCli.java            # Standalone CLI entrypoint
    │   │   ├── core/LocaraEngine.java        # Core facade & orchestrator
    │   │   ├── core/framework/               # AppFramework, FrameworkDetector, WebViewContextDetector
    │   │   ├── core/generator/               # Dynamic XPath & .rs XML Synthesizer
    │   │   ├── core/guard/                   # Danger polarity lockout guard
    │   │   ├── core/ledger/                  # Audit ledger & visual annotator
    │   │   ├── core/matcher/                 # HeuristicMatcher, StringSimilarity, UniversalSemanticDictionary
    │   │   ├── core/model/                   # Immutable domain models (UIElementNode, etc.)
    │   │   ├── core/parser/                  # PageSourceParser & HtmlDomParser
    │   │   ├── katalon/                      # In-flight hooks & keyboard/scroll guards
    │   │   └── appium/                       # Standalone Appium driver wrappers
    └── test/                                 # Unit & E2E Simulation Suite
```

---

## Organization & Community

Locara adalah proyek open-source resmi di bawah naungan **[Qureon](https://github.com/Qureon)** (*Engineering Intelligence*).

Kontribusi, pelaporan bug, dan saran perbaikan fitur sangat disambut melalui [GitHub Issues](https://github.com/Qureon/Locara/issues).

---

## Lisensi

Proyek ini dilisensikan di bawah [MIT License](LICENSE) — bebas digunakan dan dimodifikasi untuk kebutuhan open-source maupun enterprise.
