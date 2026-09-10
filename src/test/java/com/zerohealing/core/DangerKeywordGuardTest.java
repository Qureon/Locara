package com.zerohealing.core;

import com.zerohealing.core.guard.DangerKeywordGuard;
import com.zerohealing.core.model.UIElementNode;
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
}
