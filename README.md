# Locara by Qureon

<div align="center">

### Adaptive Locator Intelligence
*An intelligent self-healing engine for resilient test automation.*

[![Version](https://img.shields.io/badge/version-1.3.0-00E5FF?style=flat-square&logo=semver)](https://github.com/Qureon/Locara)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg?style=flat-square)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-25%2F25%20passing-10B981?style=flat-square)](build.gradle)
[![Java](https://img.shields.io/badge/java-17%20%7C%2021%20%7C%2024-orange?style=flat-square&logo=openjdk)](build.gradle)
[![Katalon](https://img.shields.io/badge/katalon-8.x%20%7C%209.x%20%7C%2010.x-00B0FF?style=flat-square)](https://katalon.com)
[![Appium](https://img.shields.io/badge/appium-2.x%20%7C%201.x-662d91?style=flat-square&logo=appium)](https://appium.io)

[Releases](https://github.com/Qureon/Locara/releases) • [Documentation](docs/) • [Mission Control Dashboard](dashboard/) • [MIT License](LICENSE)

</div>

---

## Overview

**Locara** *(Locator + Recovery)* adalah engine self-healing otonom generasi baru untuk otomasi mobile (Katalon Studio & Appium murni), dikembangkan di bawah organisasi **Qureon** (*Engineering Intelligence*).

Locara dirancang untuk mengatasi salah satu masalah terbesar dalam otomasi pengujian mobile: **flaky test suites akibat perubahan UI mendadak oleh developer**.

### Paradigma: In-Flight Autonomous Recovery
Fitur bawaan self-healing pada tool konvensional biasanya hanya mampu mencocokkan locator yang **sudah pernah di-spy** sebelumnya. Jika ID dirombak total atau hierarki baru ditambahkan, test suite akan gagal (*Red Suite*), alur transaksi putus, dan regresi terhenti.

Locara menerapkan prinsip **In-Flight Zero-Interruption**:
1. **In-Flight Dynamic Hotfix (<50ms)**: Saat locator target gagal ditemukan, Locara menganalisis dump XML layar aktif (`driver.getPageSource()`), menghitung vektor kemiripan heuristik (*Jaro-Winkler, Levenshtein, Resource-ID, Text, Content-Desc*), menyuntikkan XPath baru ke memori, dan melanjutkan eksekusi tanpa tes berstatus *FAIL*.
2. **Zero-Code Change via Groovy Metaprogramming**: Pengujian tetap menulis kode standar Katalon `Mobile.tap(...)` dan `Mobile.setText(...)`. Seluruh interceptor bekerja transparan di latar belakang.
3. **Instant .rs Synthesizer**: Membuat dan memperbarui berkas XML `.rs` resmi Katalon Studio (`WebElementEntity`) langsung di storage lokal, lengkap dengan backup otomatis.
4. **Enterprise Safety Guard**: Proteksi polaritas bahaya (*Danger Polarity Lockout*), auto-dismiss keyboard virtual (*The Golden Triad*), dan *Auto-Scroll Viewport Fallback* untuk tombol di bawah lipatan layar.
5. **Visual Bounding Box Evidence**: Menghasilkan screenshot UAT bergaris tepi merah dengan badge skor akurasi untuk kelengkapan audit compliance.
6. **100% Backward Compatibility**: Sepenuhnya kompatibel dengan proyek yang sebelumnya menggunakan namespace `com.zerohealing.*`.

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
│  2. Tarik dump XML layar aktif via driver.getPageSource() (<50ms)           │
│  3. Heuristic Multi-Vector Scoring (Jaro-Winkler, Levenshtein, Proximity)   │
│  4. Safety Polarity Guard: Cegah salah klik tombol 'Batal' / 'Hapus'        │
│  5. Dynamic XPath Generator menghasilkan selector optimal & presisi         │
│  6. The Golden Triad Keyboard Guard: Tutup keyboard jika tombol terhalang   │
│  7. In-Memory TestObject Mutation + Driver Action Execution                 │
│  8. Background Task: Tulis file resmi .rs ke disk & catat Audit Ledger      │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                   TEST CASE TETAP LANJUT KE STEP BERIKUTNYA!                │
│                                                                             │
│               [Input PIN] ──► [Struk Keluar] ──► PASSED (HIJAU)             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Panduan Penggunaan

### Mode 1: Invisible In-Flight Hook (Rekomendasi Utama)
> **100% Zero-Code Change.** Seluruh script lama maupun baru tetap menulis `Mobile.tap(...)` dan `Mobile.setText(...)`.

1. Letakkan `locara-1.3.0.jar` di dalam folder `Drivers/` proyek Katalon Anda.
2. Salin [`LocaraHook.groovy`](integrations/katalon/LocaraHook.groovy) ke folder `Keywords/com/qureon/locara/katalon/`.
3. Buka `Test Listeners/GlobalSetupListener.groovy`, tambahkan 1 baris berikut di `@BeforeTestSuite`:

```groovy
import com.qureon.locara.katalon.LocaraHook

class GlobalSetupListener {

    @BeforeTestSuite
    def setupTestSuite(TestSuiteContext testSuiteContext) {
        LocaraHook.install() // Inisialisasi in-flight hook
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

> **Catatan Kompatibilitas:** Jika Anda memiliki skrip eksisting yang mengimpor `com.zerohealing.katalon.ZeroHeal`, skrip tersebut tetap berjalan normal tanpa perlu diubah melalui layer backward-compatibility.

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

### Mode 4: Standalone CLI Quick-Recon
Jalankan evaluasi heuristik dan auto-generate file `.rs` langsung dari terminal:

```bash
# 1. Evaluasi & generate file .rs dari berkas XML dump
java -jar locara-1.3.0.jar --xml dump.xml --hint "Lanjut Transfer" --out "Object Repository/Transfer/btn_Lanjut.rs"

# 2. Inspeksi seluruh elemen interaktif dalam XML
java -jar locara-1.3.0.jar --xml dump.xml --inspect
```

---

## Fitur Utama

| Fitur | Komponen | Fungsi & Manfaat |
|---|---|---|
| **Danger-Lockout Policy** | `DangerKeywordGuard.java` | Mencegah salah klik fatal. Jika mencari aksi positif (*"Lanjut"* / *"Bayar"*), engine menolak tombol berpolaritas bahaya (*"Batal"*, *"Hapus"*, *"Blokir"*). |
| **Auto-Scroll Fallback** | `ScrollRecoveryEngine.java` | Otomatis melakukan *swipe down* jika tombol berada di bawah lipatan layar (*below the fold*), lalu re-dump XML. |
| **The Golden Triad** | `KeyboardGuard.java` | Deteksi posisi elemen yang tertutup keyboard virtual Android/iOS (`y > 58%`), otomatis memanggil dismiss keyboard. |
| **Visual Bounding Box** | `VisualEvidenceAnnotator.java` | Menggambar kotak merah bergaris tepi tebal & badge skor akurasi pada screenshot bukti UAT. |
| **Dynamic Re-normalization** | `HeuristicMatcher.java` | Redistribusi bobot cerdas untuk input teks/PIN (`EditText`) kosong agar skor kemiripan tidak drop. |
| **Dual Telemetry Ledger** | `HealingAuditLedger.java` | Mencatat audit log ganda (`locara_audit.json` & `locara_audit.md`) untuk integrasi CI/CD. |

---

## Konfigurasi (`locara.properties`)

Letakkan file `locara.properties` di root folder proyek untuk tuning engine:

```properties
# Master Switch: Aktifkan atau nonaktifkan engine secara global
healing.enabled=true

# Ambang batas minimal confidence (0.0 - 1.0) untuk mengeksekusi healing
healing.threshold=0.70

# Bobot Scoring Multi-Vector (Total = 1.0)
healing.weight.text=0.35
healing.weight.id=0.25
healing.weight.desc=0.20
healing.weight.class=0.10
healing.weight.proximity=0.10

# Auto-scroll jika elemen off-screen
healing.scroll.enabled=true
healing.scroll.max_attempts=3

# Pengaturan Object Repository (.rs)
healing.rs.generate=true
healing.rs.backup=true

# Audit Ledger
healing.audit.enabled=true
```

### Opt-Out Lokal (Developer Override)
Jika ada anggota tim yang ingin menonaktifkan self-healing secara lokal di komputernya:
1. Buat file `locara.local.properties` di root proyek:
   ```properties
   healing.enabled=false
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
* **XML Recon Lab**: Sandbox interaktif untuk menguji pencocokan heuristik dari raw XML.
* **UAT Sign-Off Certificate**: Dokumen sertifikat kepatuhan audit otomatis yang siap dicetak ke PDF.

Untuk menghasilkan laporan audit HTML mandiri (single-file offline):
```bash
python3 dashboard/generate_report.py
# Output: Include/evidence/locara_report.html
```

---

## Struktur Repositori

```
Locara/
├── build.gradle                              # Gradle build configuration (group: com.qureon.locara, Java 17)
├── settings.gradle                           # Project settings (rootProject.name = 'locara')
├── README.md                                 # Master Documentation & User Guide
├── LICENSE                                   # MIT License
├── dashboard/                                # Mission Control Dashboard & Telemetry Platform
│   ├── assets/                               # Vector SVG Logo & Favicon
│   ├── css/                                  # Modular Stylesheets
│   ├── js/                                   # Modular ES Architecture
│   ├── index.html                            # Master Telemetry & Recon Web UI
│   ├── server.py                             # Python HTTP & live API telemetry server
│   └── generate_report.py                    # Standalone HTML UAT sign-off report generator
├── integrations/
│   └── katalon/
│       ├── LocaraHook.groovy                 # Transparent In-Flight Groovy Interceptor
│       ├── LocaraKeywords.groovy             # Native Katalon Custom Keyword wrapper
│       └── LocaraTestListener.groovy         # Test Listener lifecycle integration
└── src/
    ├── main/java/
    │   ├── com/qureon/locara/                # Core Locara by Qureon Engine
    │   │   ├── cli/LocaraCli.java            # Standalone CLI entrypoint
    │   │   ├── core/LocaraEngine.java        # Core facade & orchestrator
    │   │   ├── core/generator/               # Dynamic XPath & .rs XML Synthesizer
    │   │   ├── core/guard/                   # Danger polarity lockout guard
    │   │   ├── core/ledger/                  # Audit ledger & visual annotator
    │   │   ├── core/matcher/                 # Jaro-Winkler, Levenshtein, heuristic scorer
    │   │   ├── core/model/                   # Immutable domain models
    │   │   ├── core/parser/                  # High-speed XML AST parser
    │   │   ├── katalon/                      # In-flight hooks & keyboard/scroll guards
    │   │   └── appium/                       # Standalone Appium driver wrappers
    │   └── com/zerohealing/                  # Backward Compatibility Layer
    └── test/                                 # Unit & E2E Simulation Suite
```

---

## Organization & Community

Locara adalah proyek open-source resmi di bawah naungan **[Qureon](https://github.com/Qureon)** (*Engineering Intelligence*).

Kontribusi, pelaporan bug, dan saran perbaikan fitur sangat disambut melalui [GitHub Issues](https://github.com/Qureon/Locara/issues).

---

## Lisensi

Proyek ini dilisensikan di bawah [MIT License](LICENSE) — bebas digunakan dan dimodifikasi untuk kebutuhan open-source maupun enterprise.
