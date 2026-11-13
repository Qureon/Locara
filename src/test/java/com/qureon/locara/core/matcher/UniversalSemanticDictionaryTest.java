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

        // Critical Banking Isolation Invariant: General OK/Confirmation must NEVER be synonymous with Financial Payment
        assertFalse(UniversalSemanticDictionary.isSynonym("ok", "bayar"), "OK must NOT be treated as synonym of Bayar");
        assertFalse(UniversalSemanticDictionary.isSynonym("setuju", "bayar"), "Setuju must NOT be treated as synonym of Bayar");
        assertFalse(UniversalSemanticDictionary.isSynonym("selesai", "bayar"), "Selesai must NOT be treated as synonym of Bayar");
        assertFalse(UniversalSemanticDictionary.isSynonym("done", "pay"), "Done must NOT be treated as synonym of Pay");

        // Verify correct clustered associations
        assertTrue(UniversalSemanticDictionary.isSynonym("bayar", "pay"));
        assertTrue(UniversalSemanticDictionary.isSynonym("bayar", "checkout"));
        assertTrue(UniversalSemanticDictionary.isSynonym("bayar", "transfer"));
        assertTrue(UniversalSemanticDictionary.isSynonym("ok", "setuju"));
        assertTrue(UniversalSemanticDictionary.isSynonym("ok", "confirm"));
        assertTrue(UniversalSemanticDictionary.isSynonym("selesai", "done"));
    }

    @Test
    @DisplayName("Should elevate composite score when app changes wording (e.g. Masuk to Login)")
    void testSynonymCompositeScoreElevation() {
        double score = StringSimilarity.compositeScore("btn_login", "Masuk");
        assertTrue(score >= 0.85, "Composite score for 'btn_login' vs 'Masuk' should be elevated >= 0.85, got: " + score);

        double nextScore = StringSimilarity.compositeScore("btn_next", "Lanjut");
        assertTrue(nextScore >= 0.85, "Composite score for 'btn_next' vs 'Lanjut' should be elevated >= 0.85, got: " + nextScore);

        // Multi-cluster word ('reload' belongs to both Top-Up and Refresh clusters)
        assertTrue(UniversalSemanticDictionary.isSynonym("reload", "topup"), "'reload' should match 'topup' cluster");
        assertTrue(UniversalSemanticDictionary.isSynonym("reload", "refresh"), "'reload' should also match 'refresh' cluster");

        // Multi-word phrase synonym affinity
        assertEquals(1.0, UniversalSemanticDictionary.calculateSemanticAffinity("isi ulang", "top up"), 0.001);
        assertEquals(1.0, UniversalSemanticDictionary.calculateSemanticAffinity("muat ulang", "refresh"), 0.001);
    }

    @Test
    @DisplayName("Should detect wealth & investment management semantic synonyms")
    void testWealthManagementSynonyms() {
        // Document & Prospectus & FFS
        assertTrue(UniversalSemanticDictionary.isSynonym("prospektus", "dokumen"));
        assertTrue(UniversalSemanticDictionary.isSynonym("prospectus", "document"));
        assertTrue(UniversalSemanticDictionary.isSynonym("fund fact sheet", "dokumen"));

        // Performance & Yield
        assertTrue(UniversalSemanticDictionary.isSynonym("kinerja", "performance"));
        assertTrue(UniversalSemanticDictionary.isSynonym("return", "imbal hasil"));
        assertTrue(UniversalSemanticDictionary.isSynonym("yield", "imbal hasil"));

        // Description & Info
        assertTrue(UniversalSemanticDictionary.isSynonym("deskripsi", "about"));
        assertTrue(UniversalSemanticDictionary.isSynonym("description", "tentang"));

        // Comparison & Benchmark
        assertTrue(UniversalSemanticDictionary.isSynonym("bandingkan", "compare"));
        assertTrue(UniversalSemanticDictionary.isSynonym("benchmark", "tolok ukur"));

        // Asset Classes
        assertTrue(UniversalSemanticDictionary.isSynonym("pendapatan tetap", "fixed income"));
        assertTrue(UniversalSemanticDictionary.isSynonym("pasar uang", "money market"));
        assertTrue(UniversalSemanticDictionary.isSynonym("saham", "equity"));
        assertTrue(UniversalSemanticDictionary.isSynonym("campuran", "balanced"));

        // Risk Profile
        assertTrue(UniversalSemanticDictionary.isSynonym("profil risiko", "risk profile"));

        // Anti-Conflicting Invariant: Inquiry concepts must NOT be synonymous with transactions
        assertFalse(UniversalSemanticDictionary.isSynonym("dokumen", "subscribe"));
        assertFalse(UniversalSemanticDictionary.isSynonym("kinerja", "redeem"));
        assertFalse(UniversalSemanticDictionary.isSynonym("deskripsi", "investasi"));
    }

    @Test
    @DisplayName("Should detect established banking domain acronyms and expand cryptic abbreviations")
    void testDomainAcronymsAndAbbreviations() {
        // Established domain acronyms
        assertTrue(UniversalSemanticDictionary.isEstablishedDomainAcronym("QRIS"));
        assertTrue(UniversalSemanticDictionary.isEstablishedDomainAcronym("rtgs"));
        assertTrue(UniversalSemanticDictionary.isEstablishedDomainAcronym("BI-FAST"));
        assertTrue(UniversalSemanticDictionary.isEstablishedDomainAcronym("mpin"));
        assertTrue(UniversalSemanticDictionary.isEstablishedDomainAcronym("kmp"));
        assertFalse(UniversalSemanticDictionary.isEstablishedDomainAcronym("randomWord"));

        // Cryptic abbreviation expansion
        assertEquals("rekening", UniversalSemanticDictionary.expandCrypticAbbreviation("rek"));
        assertEquals("transaksi", UniversalSemanticDictionary.expandCrypticAbbreviation("trx"));
        assertEquals("nomor", UniversalSemanticDictionary.expandCrypticAbbreviation("no"));
        assertEquals("user", UniversalSemanticDictionary.expandCrypticAbbreviation("usr"));
        assertEquals("manager", UniversalSemanticDictionary.expandCrypticAbbreviation("mgr"));
        assertNull(UniversalSemanticDictionary.expandCrypticAbbreviation("rekening"));
    }
}
