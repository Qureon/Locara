package com.qureon.locara.core.matcher;

import com.qureon.locara.core.model.UIElementNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class KmpMorphologyResolverTest {

    @Test
    @DisplayName("Should match scrambled PIN pad digit regardless of layout positioning")
    void testScrambledPinKeyMatching() {
        UIElementNode pinNode = new UIElementNode(
                "android.widget.TextView", "pin_key_7", "7", "",
                "android.widget.TextView", "//TextView[7]",
                new Rectangle(100, 200, 100, 100),
                true, true, true, false, false, 3
        );

        double scoreWithDigit = KmpMorphologyResolver.calculateMorphologyScore(pinNode, "7", "TAP");
        assertTrue(scoreWithDigit >= 0.95, "Should score >= 0.95 for direct PIN digit match: " + scoreWithDigit);

        double scoreWithKey = KmpMorphologyResolver.calculateMorphologyScore(pinNode, "pin_key_7", "TAP");
        assertTrue(scoreWithKey >= 0.95, "Should score >= 0.95 for pin_key_7 match: " + scoreWithKey);
    }

    @Test
    @DisplayName("Should resolve dynamic template tokens such as <key> and <i>")
    void testDynamicTokenMatching() {
        UIElementNode amountKeyNode = new UIElementNode(
                "android.widget.TextView", "transfer_amount_key_5", "Rp 50.000", "",
                "android.widget.TextView", "//TextView[5]",
                new Rectangle(100, 200, 400, 100),
                true, true, true, false, false, 3
        );

        double score = KmpMorphologyResolver.calculateMorphologyScore(
                amountKeyNode,
                "transfer_amount_key_<key>",
                "TAP"
        );
        assertTrue(score >= 0.90, "Dynamic token matching score should be >= 0.90: " + score);
    }

    @Test
    @DisplayName("Should reward action suffix affinities (_button for TAP, _input for SET_TEXT)")
    void testActionSuffixAffinity() {
        UIElementNode buttonNode = new UIElementNode(
                "android.widget.Button", "confirm_continue_button", "Lanjut", "",
                "android.widget.Button", "//Button[1]",
                new Rectangle(100, 500, 800, 100),
                true, true, true, false, false, 3
        );

        double tapScore = KmpMorphologyResolver.calculateMorphologyScore(buttonNode, "confirm continue", "TAP");
        assertTrue(tapScore >= 0.70, "TAP action on _button should be rewarded: " + tapScore);

        UIElementNode inputNode = new UIElementNode(
                "android.widget.EditText", "seluler_phone_input", "", "Input nomor ponsel",
                "android.widget.EditText", "//EditText[1]",
                new Rectangle(100, 200, 800, 100),
                true, true, true, false, false, 3
        );

        double inputScore = KmpMorphologyResolver.calculateMorphologyScore(inputNode, "seluler phone", "SET_TEXT");
        assertTrue(inputScore >= 0.70, "SET_TEXT action on _input should be rewarded: " + inputScore);
    }

    @Test
    @DisplayName("Should match newly learned QA Pack error concepts in LocaraIntentMatcher")
    void testQaPackErrorConcepts() {
        // 1. Empty nominal
        assertTrue(LocaraIntentMatcher.calculateIntentScore("Nominal Tidak Boleh Kosong", "EMPTY_NOMINAL") >= 0.85);

        // 2. Unstable network
        assertTrue(LocaraIntentMatcher.calculateIntentScore("Yah, koneksi internet tidak stabil.", "NETWORK_UNSTABLE") >= 0.85);

        // 3. No source of funds
        assertTrue(LocaraIntentMatcher.calculateIntentScore(
                "Tambahkan dan aktifkan sumber dana Tabungan/Giro/Kartu Kredit/M-Money melalui menu Pengaturan - Atur Akun",
                "NO_SOURCE_OF_FUNDS"
        ) >= 0.85);

        // 4. Session expired
        assertTrue(LocaraIntentMatcher.calculateIntentScore("Sesi Anda telah berakhir, silakan login kembali [RC06]", "SESSION_EXPIRED") >= 0.85);
    }
}
