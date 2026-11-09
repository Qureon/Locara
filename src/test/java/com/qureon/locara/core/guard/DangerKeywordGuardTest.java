package com.qureon.locara.core.guard;

import com.qureon.locara.core.model.UIElementNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.*;

public class DangerKeywordGuardTest {

    @Test
    @DisplayName("Should detect danger polarity violation when looking for positive action but node is destructive")
    void testPolarityViolationPositiveTarget() {
        UIElementNode cancelBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_cancel",
                "Batal",
                "Tombol Batal",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(100, 2000, 300, 100),
                true, true, true, false, false, 2
        );

        UIElementNode deleteBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_delete_fav",
                "Hapus",
                "Hapus Favorit",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(100, 2000, 300, 100),
                true, true, true, false, false, 2
        );

        UIElementNode lanjutBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_next",
                "Lanjut",
                "Lanjut Pembayaran",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(500, 2000, 300, 100),
                true, true, true, false, false, 2
        );

        assertTrue(DangerKeywordGuard.isPolarityViolation("Lanjut Pembayaran", cancelBtn));
        assertTrue(DangerKeywordGuard.isPolarityViolation("Bayar Sekarang", deleteBtn));
        assertFalse(DangerKeywordGuard.isPolarityViolation("Lanjut Pembayaran", lanjutBtn));
    }

    @Test
    @DisplayName("Should detect danger polarity violation when looking for cancellation but node is positive action")
    void testPolarityViolationNegativeTarget() {
        UIElementNode lanjutBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_next",
                "Lanjut",
                "Lanjut Pembayaran",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(500, 2000, 300, 100),
                true, true, true, false, false, 2
        );

        assertTrue(DangerKeywordGuard.isPolarityViolation("Batalkan Transaksi", lanjutBtn));

        UIElementNode transferBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_transfer",
                "Transfer Sekarang",
                "Transfer Sekarang",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(500, 2000, 300, 100),
                true, true, true, false, false, 2
        );
        assertTrue(DangerKeywordGuard.isPolarityViolation("Batal", transferBtn), "Cancellation must NEVER match Transfer button");
    }

    @Test
    @DisplayName("Should strictly reject destructive candidates when target is neutral (Bank Mega Zero-Loss Invariant)")
    void testStrictNeutralTargetRejectsDestructiveCandidates() {
        UIElementNode blokirBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_block_card",
                "Blokir Kartu",
                "Blokir Kartu",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(100, 2000, 300, 100),
                true, true, true, false, false, 2
        );

        UIElementNode deleteFavBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_delete_fav",
                "Hapus Daftar",
                "Hapus Daftar",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(100, 2000, 300, 100),
                true, true, true, false, false, 2
        );

        UIElementNode safeTabunganBtn = new UIElementNode(
                "android.widget.TextView",
                "com.bankmega.msmile:id/tv_account_detail",
                "Detail Tabungan",
                "Detail Tabungan",
                "android.widget.TextView",
                "//android.widget.TextView",
                new Rectangle(100, 500, 400, 100),
                true, true, true, false, false, 2
        );

        // Neutral target must reject destructive buttons
        assertTrue(DangerKeywordGuard.isPolarityViolation("Informasi Kartu", blokirBtn));
        assertTrue(DangerKeywordGuard.isPolarityViolation("Daftar Favorit", deleteFavBtn));

        // Neutral target matching neutral node is safe
        assertFalse(DangerKeywordGuard.isPolarityViolation("Tabungan Saya", safeTabunganBtn));
    }

    @Test
    @DisplayName("Should strictly reject financial action candidates when target is generic/neutral (Bank Mega Zero-Loss Invariant)")
    void testStrictFinancialCommitmentIsolation() {
        UIElementNode bayarBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_pay",
                "Bayar",
                "Bayar Tagihan",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(100, 2000, 300, 100),
                true, true, true, false, false, 2
        );

        UIElementNode transferBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_transfer",
                "Transfer",
                "Transfer Saldo",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(100, 2000, 300, 100),
                true, true, true, false, false, 2
        );

        UIElementNode checkoutBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_checkout",
                "Checkout",
                "Checkout Keranjang",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(100, 2000, 300, 100),
                true, true, true, false, false, 2
        );

        // When script looks for neutral/affirmation (e.g. 'OK', 'Lanjut', 'Tutup', 'Simpan'),
        // selecting financial commitment buttons MUST be rejected!
        assertTrue(DangerKeywordGuard.isPolarityViolation("OK", bayarBtn), "Generic 'OK' must NEVER match 'Bayar'");
        assertTrue(DangerKeywordGuard.isPolarityViolation("Lanjut", transferBtn), "Generic 'Lanjut' must NEVER match 'Transfer'");
        assertTrue(DangerKeywordGuard.isPolarityViolation("Tutup Dialog", bayarBtn), "Dismissal must NEVER match 'Bayar'");
        assertTrue(DangerKeywordGuard.isPolarityViolation("Simpan Dokumen", checkoutBtn), "Generic save must NEVER match 'Checkout'");

        // When target DOES explicitly request financial action, it is allowed
        assertFalse(DangerKeywordGuard.isPolarityViolation("Bayar Sekarang", bayarBtn), "Explicit pay target should allow Pay button");
        assertFalse(DangerKeywordGuard.isPolarityViolation("Transfer Saldo", transferBtn), "Explicit transfer target should allow Transfer button");
        assertFalse(DangerKeywordGuard.isPolarityViolation("Konfirmasi Pembayaran", bayarBtn), "Confirmation with payment intent should allow Pay button");
    }

    @Test
    @DisplayName("Should block mixed-polarity destructive buttons ('Ya, Hapus', 'Konfirmasi Batal') when target is positive")
    void testMixedPolarityDestructiveLockoutAndWordBoundaries() {
        UIElementNode yaHapusBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btn_confirm_delete",
                "Ya, Hapus",
                "Konfirmasi Penghapusan",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(100, 2000, 300, 100),
                true, true, true, false, false, 2
        );

        UIElementNode kirimUangBtn = new UIElementNode(
                "android.widget.Button",
                "com.bankmega.msmile:id/btnKirimUang",
                "Kirim Uang",
                "Kirim Uang Sekarang",
                "android.widget.Button",
                "//android.widget.Button",
                new Rectangle(100, 2000, 300, 100),
                true, true, true, false, false, 2
        );

        UIElementNode promoMenarikNode = new UIElementNode(
                "android.widget.TextView",
                "com.bankmega.msmile:id/tv_promo",
                "Promo Menarik Untuk Beliau",
                "Promo Menarik",
                "android.widget.TextView",
                "//android.widget.TextView",
                new Rectangle(100, 500, 300, 100),
                true, true, true, false, false, 2
        );

        assertTrue(DangerKeywordGuard.isPolarityViolation("Lanjut", yaHapusBtn),
                "Positive target 'Lanjut' MUST block mixed-polarity destructive button 'Ya, Hapus'");
        assertTrue(DangerKeywordGuard.isPolarityViolation("OK", kirimUangBtn),
                "Generic 'OK' MUST block financial 'Kirim Uang' button");
        assertFalse(DangerKeywordGuard.isPolarityViolation("Kirim Uang", kirimUangBtn),
                "Explicit 'Kirim Uang' target should allow 'Kirim Uang' button");
        assertFalse(DangerKeywordGuard.isPolarityViolation("Promo", promoMenarikNode),
                "Substrings like 'menarik' or 'beliau' must NOT trigger false-positive financial lockout");

        // React Native clickable ViewGroup wrapping child TextView with 'Hapus' or 'Bayar'
        UIElementNode rnDeleteContainer = new UIElementNode(
                "android.view.ViewGroup", "", "", "", "android.view.ViewGroup", "//android.view.ViewGroup[1]",
                new Rectangle(100, 2000, 300, 100), true, true, true, false, false, 2
        );
        UIElementNode childDeleteText = new UIElementNode(
                "android.widget.TextView", "", "Hapus Akun", "", "android.widget.TextView", "//android.widget.TextView[1]",
                new Rectangle(120, 2020, 260, 60), false, true, true, false, false, 3
        );
        rnDeleteContainer.addChild(childDeleteText);

        assertTrue(DangerKeywordGuard.isPolarityViolation("Lanjut", rnDeleteContainer),
                "Clickable ViewGroup wrapping 'Hapus Akun' child TextView MUST be blocked by DangerKeywordGuard");
    }

    @Test
    @DisplayName("Wealth & Investment Financial Isolation: Inquiry targets MUST block transaction buttons (Subscribe, Redeem, Cairkan)")
    void testWealthFinancialPolarityIsolation() {
        UIElementNode subscribeBtn = new UIElementNode(
                "android.widget.Button", "com.bankmega.msmile:id/btn_subscribe",
                "Subscribe", "Subscribe Unit Reksa Dana", "android.widget.Button", "//android.widget.Button",
                new Rectangle(100, 2000, 300, 100), true, true, true, false, false, 2
        );

        UIElementNode redeemBtn = new UIElementNode(
                "android.widget.Button", "com.bankmega.msmile:id/btn_redeem",
                "Redeem", "Cairkan Unit Investasi", "android.widget.Button", "//android.widget.Button",
                new Rectangle(500, 2000, 300, 100), true, true, true, false, false, 2
        );

        UIElementNode switchingBtn = new UIElementNode(
                "android.widget.Button", "com.bankmega.msmile:id/btn_switch",
                "Switching", "Pengalihan Unit", "android.widget.Button", "//android.widget.Button",
                new Rectangle(100, 2000, 300, 100), true, true, true, false, false, 2
        );

        UIElementNode deskripsiTab = new UIElementNode(
                "android.widget.TextView", "com.bankmega.msmile:id/tab_deskripsi",
                "Deskripsi", "Tab Deskripsi Produk", "android.widget.TextView", "//android.widget.TextView",
                new Rectangle(100, 300, 200, 60), true, true, true, false, false, 2
        );

        // When test script searches for inquiry tabs, transaction buttons MUST be strictly blocked
        assertTrue(DangerKeywordGuard.isPolarityViolation("Deskripsi", subscribeBtn),
                "Inquiry target 'Deskripsi' MUST block 'Subscribe' financial button");
        assertTrue(DangerKeywordGuard.isPolarityViolation("Dokumen", redeemBtn),
                "Inquiry target 'Dokumen' MUST block 'Redeem' financial button");
        assertTrue(DangerKeywordGuard.isPolarityViolation("Kinerja", switchingBtn),
                "Inquiry target 'Kinerja' MUST block 'Switching' financial button");
        assertTrue(DangerKeywordGuard.isPolarityViolation("Bandingkan Produk", subscribeBtn),
                "Inquiry target 'Bandingkan' MUST block 'Subscribe' financial button");

        // When test script explicitly requests wealth transactions, they are safely permitted
        assertFalse(DangerKeywordGuard.isPolarityViolation("Subscribe Reksa Dana", subscribeBtn),
                "Explicit 'Subscribe' target should allow 'Subscribe' button");
        assertFalse(DangerKeywordGuard.isPolarityViolation("Cairkan Reksa Dana", redeemBtn),
                "Explicit 'Cairkan' target should allow 'Redeem' button");
        assertFalse(DangerKeywordGuard.isPolarityViolation("Deskripsi", deskripsiTab),
                "Inquiry target 'Deskripsi' safely allows 'Deskripsi' tab");
    }

    @Test
    @DisplayName("Corporate Issuer Isolation: Product targets MUST strictly reject corporate issuer headers (PT ... Aset Manajemen)")
    void testCorporateIssuerIsolation() {
        UIElementNode manulifeIssuerHeader = new UIElementNode(
                "android.widget.TextView", "com.bankmega.msmile:id/tv_issuer",
                "PT Manulife Aset Manajemen Indonesia", "Header Manajer Investasi",
                "android.widget.TextView", "//android.widget.TextView[1]",
                new Rectangle(50, 100, 800, 80), false, false, false, false, false, 1
        );

        UIElementNode schroderIssuerHeader = new UIElementNode(
                "android.widget.TextView", "com.bankmega.msmile:id/tv_issuer",
                "PT Schroder Investment Management Indonesia", "Header Manajer Investasi",
                "android.widget.TextView", "//android.widget.TextView[1]",
                new Rectangle(50, 100, 800, 80), false, false, false, false, false, 1
        );

        UIElementNode productCard = new UIElementNode(
                "android.widget.TextView", "com.bankmega.msmile:id/tv_product_title",
                "Reksa Dana Manulife Dana Campuran II", "Kartu Produk Reksadana",
                "android.widget.TextView", "//android.widget.TextView[2]",
                new Rectangle(50, 500, 800, 100), true, true, true, false, false, 2
        );

        // Product target MUST block corporate company header
        assertTrue(DangerKeywordGuard.isPolarityViolation("reksa dana manulife dana campuran", manulifeIssuerHeader),
                "Product target MUST reject 'PT Manulife Aset Manajemen Indonesia' header");
        assertTrue(DangerKeywordGuard.isPolarityViolation("Schroder Dana Prestasi Plus", schroderIssuerHeader),
                "Product target MUST reject 'PT Schroder Investment Management Indonesia' header");

        // Product target safely matches product card
        assertFalse(DangerKeywordGuard.isPolarityViolation("reksa dana manulife dana campuran", productCard),
                "Product target safely allows product card");

        // Explicit corporate query safely allows corporate header
        assertFalse(DangerKeywordGuard.isPolarityViolation("PT Manulife Aset Manajemen Indonesia", manulifeIssuerHeader),
                "Explicit PT target should allow corporate issuer header");
    }
}
