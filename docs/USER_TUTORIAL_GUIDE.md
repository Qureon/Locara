# Panduan Lengkap Penggunaan Locara by Qureon (v1.6.0)

> **Mobile & Hybrid Test Automation Self-Healing Engine (Katalon Studio & Appium)**  
> **Engineering**: Qureon Open Source (`Engineering Intelligence`)  
> **Target**: Mobile & Hybrid Web Automation (Android, iOS, Flutter, React Native, WebView)  
> **Versi Terbaru**: v1.6.0 Universal Enterprise  

---

## 📌 1. Apa itu Locara by Qureon?

**Locara** *(Locator + Recovery)* adalah *autonomous self-healing & semantic assertion engine* generasi baru yang bertindak sebagai **"Co-Pilot Cerdas & Dokter Bedah In-Flight"** pada proyek pengujian otomasi mobile Anda.

Tujuan utamanya adalah menjaga kestabilan *test suite* agar regresi berjalan mulus tanpa terputus di tengah jalan akibat:
1. Pengembang mengubah `resource-id`, hierarki DOM, atau teks tombol di UI.
2. XPath berpindah posisi atau dibungkus di dalam komponen kontainer baru (misal Jetpack Compose atau Bottom Sheet).
3. Tombol tertutup oleh virtual keyboard Android/iOS (*auto-dismiss via The Golden Triad*).
4. Tombol berada di bawah lipatan layar (*below the fold*) dan membutuhkan *auto-scroll*.
5. Halaman berada di dalam embedded Hybrid WebView (HTML DOM) yang biasanya membuat engine native gagal.
6. Validasi teks respons backend memiliki variasi semantik minor.

---

## 🤝 2. Kebijakan Kebebasan Tim: Zero-Friction Coexistence

Di tim QA enterprise, kami sangat menghormati preferensi masing-masing engineer:
- Ada rekan yang **ingin menggunakan Locara** agar regresi overnight tidak gampang gagal dan bebas capek rekam ulang Object Repository.
- Ada rekan yang **ingin menggunakan vanilla Katalon** (menginginkan test langsung *fail* jika ada perbedaan locator sekecil apapun).

Oleh karena itu, Locara dirancang dengan prinsip **Zero Compile-Time Coupling (Ghost Mode)**:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   ARSITEKTUR GHOST MODE (PLUGGABLE)                    │
│                                                                        │
│  [Repo Git Bank / Tim] ───► 100% BEBAS DARI FILE BINER .JAR            │
│  [Rekan Tanpa Locara]  ───► Proyek 100% HIJAU (0 Error, Native)        │
│  [Rekan Pakai Locara]  ───► Cukup letakkan JAR di Drivers/ lokal       │
│                             Engine langsung auto-detect & aktif!       │
└────────────────────────────────────────────────────────────────────────┘
```

1. **Kode Bersih Tanpa Hard Dependency**: File `Keywords/` menggunakan *dynamic reflection*. Jika JAR tidak ada di laptop rekan tim, kode diam-diam melanjutkan flow native Katalon tanpa error compile sama sekali.
2. **Git Bersih**: Folder `Drivers/locara*.jar` masuk dalam `.gitignore` sehingga file biner tidak membebani repo git tim.

---

## ⚡ 3. Cara Instalasi Cepat (Pilih Salah Satu)

### Cara A: Minta AI Anda yang Mengunduh (Paling Mudah)
Jika Anda menggunakan AI assistant (Antigravity IDE, Cursor, Claude Code, GitHub Copilot):
Salin prompt berikut dan tempel ke jendela chat AI Anda:

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

---

### Cara B: 1-Line Command di Terminal / PowerShell

**Di Windows (PowerShell):**
```powershell
$tag = (Invoke-RestMethod "https://api.github.com/repos/Qureon/Locara/releases/latest").tag_name; $ver = $tag.TrimStart('v'); Invoke-WebRequest -Uri "https://github.com/Qureon/Locara/releases/download/$tag/locara-$ver.jar" -OutFile "Drivers\locara-$ver.jar"; Write-Host "✅ Locara $tag berhasil disuntikkan ke Drivers!" -ForegroundColor Green
```

**Di macOS / Linux (Terminal / Bash):**
```bash
TAG=$(curl -s https://api.github.com/repos/Qureon/Locara/releases/latest | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/') && curl -sL -o Drivers/locara-${TAG#v}.jar https://github.com/Qureon/Locara/releases/download/$TAG/locara-${TAG#v}.jar && echo "✅ Locara $TAG berhasil disuntikkan ke Drivers!"
```

---

## 🚀 4. Cara Penggunaan dalam Skrip Pengujian

### Mode 1: Invisible In-Flight Hook (Rekomendasi Utama)
**100% Zero-Code Change.** Anda tetap menulis test case seperti biasa:

```groovy
// Kode pengujian Anda tetap standar Katalon!
Mobile.tap(findTestObject('Object Repository/Transfer/btn_Lanjut'), 10)
Mobile.setText(findTestObject('Object Repository/Transfer/input_Nominal'), '50000', 10)
Mobile.getText(findTestObject('Object Repository/Transfer/lbl_TotalBayar'), 10)
```

- **Saat locator normal**: Locara tidur (*zero overhead, 0 ms delay*).
- **Saat locator gagal**: Locara menyambar kegagalan dalam <5ms, memindai DOM layar aktif, menemukan elemen yang tepat, dan langsung mengeksekusi ulang aksi tanpa test case berstatus *FAILED*.

---

## 🛠️ 5. Troubleshooting & FAQ Tim

### Q1: Kenapa di Katalon muncul pesan error `unable to resolve class CustomKeywords` atau `LocaraIntentMatcher`?
**Penyebab:**  
Katalon Studio di Windows menyimpan cache keyword lama di dalam file internal `Libs/CustomKeywords.groovy`.

**Solusi 1 Langkah:**  
1. Di menu atas Katalon Studio, klik menu: **Project** ➡️ **Clean...**
2. Pilih opsi **Clean all projects**, lalu klik tombol **Clean**.
3. Tutup dan buka kembali Katalon Studio. Seluruh error merah langsung lenyap 100%!

---

### Q2: Bagaimana jika saya ingin mematikan Locara secara total di laptop saya?
Cukup buat file bernama **`locara.local.properties`** di folder root proyek Anda:
```properties
locara.enabled=false
```
*File `*.local.properties` sudah otomatis masuk ke dalam `.gitignore`, sehingga setting ini 100% privat dan tidak akan mempengaruhi rekan lain saat git push.*

---

### Q3: Apakah verifikasi angka / saldo rekening bisa salah (*False-Green*)?
**MUTLAK TIDAK.**  
Validasi saldo rekening, nomor rekening, nominal transfer, dan tarif biaya admin menggunakan pencocokan integer/long yang ketat. Locara **tidak pernah** memberikan toleransi pada angka nominal finansial. Jika berbeda Rp 1 pun, tes akan tetap gagal dengan tegas.

---

### Q4: Apakah Object Repository (`.rs`) hasil spy manual saya akan tertimpa?
**TIDAK.**  
Locara bekerja secara default dengan prinsip *in-memory runtime patching*. File fisik `.rs` manual Anda tidak akan disentuh atau diubah tanpa izin eksplisit.

---

## 📚 Dokumentasi Terkait
- [Arsitektur Lengkap & Diagram Alur](ARCHITECTURE_AND_FLOW.md)
- [Rencana Pengembangan Universal v1.6.0](v1.6_HYBRID_AND_UNIVERSAL_ENTERPRISE_PLAN.md)
- [Repositori Resmi GitHub](https://github.com/Qureon/Locara)
