package com.qureon.locara.core.matcher;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LocaraIntentMatcherTest {

    @Test
    public void shouldMatchBankMegaPhoneLengthNegativeError() {
        String screenText = "No handphone tidak valid karena kurang dari 10 digit";
        List<String> expectedIntents = Arrays.asList(
                "INVALID_PHONE_NUMBER",
                "NOMOR_TIDAK_VALID",
                "PHONE_NUMBER_FORMAT",
                "KURANG_DARI_10_DIGIT"
        );

        LocaraIntentMatcher.IntentMatchResult result =
                LocaraIntentMatcher.evaluateScreenIntents(Arrays.asList(screenText), expectedIntents, 0.70);

        assertTrue(result.isMatched(), "Should successfully match negative test intent");
        assertTrue(result.getConfidence() >= 0.85, "Confidence should be high (>= 85%)");
        assertEquals(screenText, result.getActualText());
    }

    @Test
    public void shouldMatchPhonePrefixError() {
        String screenText = "No handphone harus diawali dengan 08";
        List<String> expectedIntents = Arrays.asList(
                "HARUS_DIAWALI_08",
                "NOMOR_TIDAK_VALID"
        );

        LocaraIntentMatcher.IntentMatchResult result =
                LocaraIntentMatcher.evaluateScreenIntents(Arrays.asList(screenText), expectedIntents, 0.70);

        assertTrue(result.isMatched());
        assertTrue(result.getConfidence() >= 0.85);
    }

    @Test
    public void shouldMatchInsufficientBalanceError() {
        String screenText = "Saldo rekening Anda tidak mencukupi untuk melakukan transaksi ini";
        List<String> expectedIntents = Arrays.asList(
                "SALDO_TIDAK_CUKUP",
                "INSUFFICIENT_BALANCE"
        );

        LocaraIntentMatcher.IntentMatchResult result =
                LocaraIntentMatcher.evaluateScreenIntents(Arrays.asList(screenText), expectedIntents, 0.70);

        assertTrue(result.isMatched());
        assertTrue(result.getConfidence() >= 0.85);
    }

    @Test
    public void shouldRejectUnrelatedScreenTexts() {
        List<String> onScreenTexts = Arrays.asList(
                "Cari Favorit",
                "Input Nomor Pelanggan Baru",
                "Daftar Favorit",
                "Anda belum pernah transaksi",
                "Lanjut"
        );
        List<String> expectedIntents = Arrays.asList(
                "KURANG_DARI_10_DIGIT",
                "INVALID_PHONE_NUMBER"
        );

        LocaraIntentMatcher.IntentMatchResult result =
                LocaraIntentMatcher.evaluateScreenIntents(onScreenTexts, expectedIntents, 0.70);

        assertFalse(result.isMatched(), "Unrelated screen texts must NOT match negative error codes");
    }
}
