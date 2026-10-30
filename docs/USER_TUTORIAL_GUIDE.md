# Panduan Penggunaan Locara by Qureon

> **Mobile Test Automation Self-Healing Engine (Katalon Studio & Appium)**  
> **Engineering**: Qureon Open Source  
> **Target**: Mobile Automation (Android & iOS) via Katalon Studio & Appium  

---

## 📌 1. Apa itu Locara by Qureon?

**Locara** *(Locator + Recovery)* by **Qureon** adalah *autonomous self-healing & semantic assertion engine* yang dirancang untuk menjaga kestabilan *test suite* regresi mobile tanpa perlu mengubah kode tes eksisting.

Tujuan utamanya adalah **menjadi Airbag / Jaring Pengaman Cerdas**:
- Mencegah test case regresi malam hari (*overnight regression*) gagal hanya karena:
  1. Pengembang mengubah sedikit `resource-id`, `class`, atau hierarki tombol di UI.
  2. Tombol tertutup oleh virtual keyboard Android/iOS (*auto-dismiss via The Golden Triad*).
  3. Tombol berada di bawah lipatan layar (*below the fold*) dan butuh sedikit *auto-scroll*.
  4. Label struk memiliki perbedaan minor pada spasi, tanda baca, atau urutan kata semantik.

---

## 🤝 2. Kebijakan Kebebasan Tim: Mau Pakai vs Tidak Mau Pakai

Di tim QA enterprise, kami sangat menghormati alur kerja masing-masing rekan kerja:
- Ada rekan yang **mau pakai** agar regresi tidak terputus di tengah jalan.
- Ada rekan yang **tidak mau pakai** karena lebih menyukai *vanilla Katalon* (ingin test langsung *fail* jika locator berubah).

Oleh karena itu, Locara dirancang dengan sistem **Zero-Friction Coexistence**:

### A. Bagi Rekan yang INGIN MENGGUNAKAN (Default)
**Anda tidak perlu mengubah satu baris pun kode test case Anda!**
- Tetap gunakan keyword standar: `Mobile.tap(...)`, `Mobile.setText(...)`, `Mobile.getText(...)`, `Mobile.verifyElementText(...)`.
- Tetap gunakan helper standar: `VerificationHelper.verificationValidationText(...)`.
- Saat elemen normal: Engine tidur (*zero overhead, 0 ms delay, zero log pollution*).
- Saat locator bermasalah: Engine otomatis menganalisis hierarki layar aktif, menyuntikkan XPath baru ke memori, dan melanjutkan eksekusi dengan status **PASSED (HIJAU)**.

---

### B. Bagi Rekan yang TIDAK INGIN MENGGUNAKAN (Opt-Out 100% Murni)
Jika Anda ingin mematikan Locara secara total di laptop Anda:

1. Buat file baru bernama **`locara.local.properties`** di folder root proyek:
   ```properties
   locara.enabled=false
   ```
2. Simpan file tersebut.

> [!NOTE]
> File `*.local.properties` **sudah otomatis masuk dalam `.gitignore`**!  
> Pengaturan ini 100% lokal di laptop Anda, **tidak akan pernah ter-commit ke Git atau menimbulkan *merge conflict* dengan rekan lain**.

**Efek saat `locara.enabled=false`:**
- `LocaraHook` otomatis mundur dan **tidak menyentuh/mengubah keyword Katalon sama sekali**.
- Semua keyword `Mobile.*` berjalan 100% murni *engine* asli Katalon.
- `VerificationHelper` kembali ke metode pencocokan teks konvensional tanpa bantuan semantik.
- Tidak ada log `[Locara]`, eksekusi kembali persis seperti sebelum ada engine.

---

## ⚙️ 3. Opsi Sakelar Lainnya (Advanced)

Selain menggunakan file properties lokal, Anda juga dapat mengatur sakelar melalui:

| Metode | Cara Konfigurasi | Cocok Untuk |
|---|---|---|
| **System Property (JVM)** | Tambahkan argumen `-Dlocara.enabled=false` pada runner. | CI/CD Pipeline / Command Line Runner |
| **Environment Variable** | Set variabel OS `LOCARA_ENABLED=false` atau `QUREON_ENABLED=false` di terminal Anda. | Terminal / Mac zsh environment |
| **Execution Profile** | Tambahkan `ENABLE_LOCARA = false` pada profil Katalon (misal `default.glbl`). | Eksekusi manual via Katalon Studio GUI |

---

## 🛡️ 4. Menjawab Kekhawatiran Umum (FAQ & Mitos)

### Q1: Apakah Locara bisa salah klik tombol berbahaya (misal tombol *Batal* atau *Hapus*)?
**TIDAK.**  
Locara dilengkapi dengan **`DangerKeywordGuard`**. Jika skrip Anda mencari tombol aksi positif (seperti *"Lanjut"*, *"Bayar"*, *"Konfirmasi"*, *"Kirim"*), engine **secara mutlak menolak** elemen yang mengandung kata-kata destruktif (*"Batal"*, *"Hapus"*, *"Cancel"*, *"Delete"*, *"Blokir"*, *"Keluar"*). Jika tidak menemukan elemen yang aman, engine akan membiarkan test *fail* secara terhormat.

### Q2: Apakah verifikasi nominal uang atau saldo rekening bisa salah (*False-Green*)?
**TIDAK.**  
Seluruh validasi numerik di `VerificationHelper.verificationNumeric` **tetap 100% strict integer/long**. Validasi saldo, nominal transfer, biaya admin, dan total pembayaran tidak menggunakan toleransi semantik. Jika nominal berbeda 1 Rupiah pun, status tetap **MISMATCH ❌ (FAILED)**.

### Q3: Apakah file Object Repository (`.rs`) hasil spy manual saya akan ditimpa?
**TIDAK.**  
Locara beroperasi dengan aturan **Strict Anti-Tampering**. Secara default, engine melakukan *in-memory patching* di RAM tanpa menimpa file fisik `.rs` Anda. Jika fitur pembuatan `.rs` diaktifkan, file baru akan diarahkan ke folder karantina terisolasi (`Object Repository/Healed/`) atau disimpan sebagai file cadangan `.healed.rs`.

### Q4: Apakah Locara memperlambat eksekusi test case?
**TIDAK.**  
Locara adalah *passive interceptor*. Selama locator asli Anda ditemukan oleh Katalon, Locara tidak melakukan proses apapun (passthrough biasa). Analisis XML hanya berjalan jika locator asli gagal ditemukan (`NoSuchElementException`).

---

## Kontak & Kontribusi
Jika ada pertanyaan, kendala, atau ingin menyarankan perbaikan pada engine Locara, silakan ajukan issue di GitHub repository resmi: [Qureon/Locara](https://github.com/Qureon/Locara).
