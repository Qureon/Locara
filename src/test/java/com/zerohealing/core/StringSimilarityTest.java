package com.zerohealing.core;

import com.zerohealing.core.matcher.StringSimilarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StringSimilarityTest {

    @Test
    @DisplayName("Jaro-Winkler should recognize identical and typo-variant strings")
    void testJaroWinkler() {
        assertEquals(1.0, StringSimilarity.jaroWinkler("Lanjut", "Lanjut"), 0.001);
        assertTrue(StringSimilarity.jaroWinkler("Konfirmasi", "Konfirmasii") > 0.90);
        assertTrue(StringSimilarity.jaroWinkler("btn_lanjut", "lanjut") > 0.80);
    }

    @Test
    @DisplayName("Token overlap should match snake_case against spaced text")
    void testTokenOverlap() {
        double overlap = StringSimilarity.tokenOverlap("btn_submit_transfer", "Submit Transfer");
        assertTrue(overlap >= 0.60, "Expected token overlap >= 0.60, got: " + overlap);
    }

    @Test
    @DisplayName("Composite score should reward semantic matches and substring matches")
    void testCompositeScore() {
        double score = StringSimilarity.compositeScore("btn_lanjut_bayar", "Lanjut Bayar");
        assertTrue(score >= 0.80, "Expected composite score >= 0.80, got: " + score);

        double lowScore = StringSimilarity.compositeScore("btn_cancel", "Lanjut Bayar");
        assertTrue(lowScore < 0.40, "Expected low score for unrelated strings, got: " + lowScore);
    }

    @Test
    @DisplayName("Should recognize Topup & Bayar against Top Up dan Bayar with 100% confidence")
    void testIndonesianBankingSynonymMatching() {
        double score = StringSimilarity.compositeScore("Topup & Bayar", "Top Up dan Bayar");
        assertEquals(1.0, score, 0.001, "Expected exact match for Topup & Bayar vs Top Up dan Bayar");
    }

    @Test
    @DisplayName("cleanSemanticHint should sanitize full Katalon Object Repository paths")
    void testCleanSemanticHint() {
        String raw = "Object Repository/Dashboard/01. ViewGroup - Top Up dan Bayar";
        String cleaned = com.zerohealing.core.matcher.HeuristicMatcher.cleanSemanticHint(raw);
        assertEquals("Top Up dan Bayar", cleaned);

        String buttonRaw = "Object Repository/Login/02. Button - Lanjut Transaksi.rs";
        assertEquals("Lanjut Transaksi", com.zerohealing.core.matcher.HeuristicMatcher.cleanSemanticHint(buttonRaw));

        // Bank Mega 2-tier sub-index (05-2. TextView - Value Nama Produk)
        String twoTier = "Object Repository/Dashboard/Top Up/05-2. TextView - Value Nama Produk.rs";
        assertEquals("Nama Produk", com.zerohealing.core.matcher.HeuristicMatcher.cleanSemanticHint(twoTier));

        // GroupView prefix and Tombol
        String groupView = "Object Repository/Dashboard/Apply/04. GroupView - Tombol Kembali Di Halaman Produk.rs";
        assertEquals("Kembali Di Halaman Produk", com.zerohealing.core.matcher.HeuristicMatcher.cleanSemanticHint(groupView));

        // Button without space after dash (-Tombol)
        String noSpaceDash = "Object Repository/Dashboard/Apply/05. Button -Tombol OK no path file di deskrpsi 1.rs";
        assertEquals("OK no path file di deskrpsi 1", com.zerohealing.core.matcher.HeuristicMatcher.cleanSemanticHint(noSpaceDash));

        // Banner prefix
        String banner = "Object Repository/Dashboard/Ajak Teman/01. Banner - Ajak Teman.rs";
        assertEquals("Ajak Teman", com.zerohealing.core.matcher.HeuristicMatcher.cleanSemanticHint(banner));

        // Box prefix
        String box = "Object Repository/Dashboard/Apply/Reksa Dana/Kinerja/05. TextView - Box 1 Day.rs";
        assertEquals("1 Day", com.zerohealing.core.matcher.HeuristicMatcher.cleanSemanticHint(box));

        // Label qualifier
        String label = "Object Repository/Dashboard/05-1. TextView - Label Detail Produk.rs";
        assertEquals("Detail Produk", com.zerohealing.core.matcher.HeuristicMatcher.cleanSemanticHint(label));
    }

    @Test
    @DisplayName("Should recognize symbols like &, ', (), /, - seamlessly against sanitized Katalon names")
    void testSpecialSymbolsAndPunctuationMatching() {
        // Apostrophe & punctuation
        double dontScore = StringSimilarity.compositeScore("Don't show again", "Dont Show Again");
        assertTrue(dontScore >= 0.95, "Expected high score for apostrophe variation, got: " + dontScore);

        // Parentheses & abbreviations
        double snkScore = StringSimilarity.compositeScore("Syarat & Ketentuan (S&K)", "Syarat dan Ketentuan");
        assertTrue(snkScore >= 0.85, "Expected high score for parentheses variation, got: " + snkScore);

        // Slashes & dashes
        double infoScore = StringSimilarity.compositeScore("Info / Bantuan", "Info Bantuan");
        assertTrue(infoScore >= 0.85, "Expected high score for slash variation, got: " + infoScore);
    }
}
