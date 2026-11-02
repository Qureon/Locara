package com.qureon.locara.core.matcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UniversalSemanticDictionaryTest {

    @Test
    @DisplayName("Should detect cross-lingual universal mobile synonyms (Indonesian - English)")
    void testUniversalSynonyms() {
        assertTrue(UniversalSemanticDictionary.isSynonym("masuk", "login"));
        assertTrue(UniversalSemanticDictionary.isSynonym("lanjut", "next"));
        assertTrue(UniversalSemanticDictionary.isSynonym("batal", "cancel"));
        assertTrue(UniversalSemanticDictionary.isSynonym("bayar", "pay"));
        assertTrue(UniversalSemanticDictionary.isSynonym("konfirmasi", "confirm"));
        assertTrue(UniversalSemanticDictionary.isSynonym("cari", "search"));
        assertTrue(UniversalSemanticDictionary.isSynonym("simpan", "save"));
        assertTrue(UniversalSemanticDictionary.isSynonym("hapus", "delete"));
        assertTrue(UniversalSemanticDictionary.isSynonym("topup", "isi ulang"));
    }

    @Test
    @DisplayName("Should reject conflicting or unrelated terms")
    void testRejectNonSynonyms() {
        assertFalse(UniversalSemanticDictionary.isSynonym("batal", "lanjut"));
        assertFalse(UniversalSemanticDictionary.isSynonym("hapus", "simpan"));
        assertFalse(UniversalSemanticDictionary.isSynonym("login", "logout"));
        assertFalse(UniversalSemanticDictionary.isSynonym("cari", "bayar"));
    }

    @Test
    @DisplayName("Should elevate composite score when app changes wording (e.g. Masuk to Login)")
    void testSynonymCompositeScoreElevation() {
        double score = StringSimilarity.compositeScore("btn_login", "Masuk");
        assertTrue(score >= 0.85, "Composite score for 'btn_login' vs 'Masuk' should be elevated >= 0.85, got: " + score);

        double nextScore = StringSimilarity.compositeScore("btn_next", "Lanjut");
        assertTrue(nextScore >= 0.85, "Composite score for 'btn_next' vs 'Lanjut' should be elevated >= 0.85, got: " + nextScore);
    }
}
