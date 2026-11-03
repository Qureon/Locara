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
    }
}
